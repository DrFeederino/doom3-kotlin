/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/DeclParticle.h
 *                  neo/framework/DeclParticle.cpp
 *
 * Doom 3 GPL Source Code
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package neo.framework

import neo.Renderer.Material
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderView_s
import neo.TempDump
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.framework.DeclTable.idDeclTable
import neo.framework.File_h.idFile
import neo.framework.File_h.idFile_Memory
import neo.idlib.BV.idBounds
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CFloat
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.idException
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idMath
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.idlib.math.vec3_origin
import java.util.*
import kotlin.math.sqrt

object DeclParticle {
    val ParticleCustomDesc: Array<ParticleParmDesc> = arrayOf(
        ParticleParmDesc("standard", 0, "Standard"),
        ParticleParmDesc("helix", 5, "sizeX Y Z radialSpeed axialSpeed"),
        ParticleParmDesc("flies", 3, "radialSpeed axialSpeed size"),
        ParticleParmDesc("orbit", 2, "radius speed"),
        ParticleParmDesc("drip", 2, "something something")
    )

    //const int CustomParticleCount = sizeof( ParticleCustomDesc ) / sizeof( const ParticleParmDesc );
    val CustomParticleCount = ParticleCustomDesc.size
    val ParticleDirectionDesc: Array<ParticleParmDesc> = arrayOf(
        ParticleParmDesc("cone", 1, ""),
        ParticleParmDesc("outward", 1, "")
    )
    val ParticleDistributionDesc: Array<ParticleParmDesc> = arrayOf(
        ParticleParmDesc("rect", 3, ""),
        ParticleParmDesc("cylinder", 4, ""),
        ParticleParmDesc("sphere", 3, "")
    )
    val ParticleOrientationDesc: Array<ParticleParmDesc> = arrayOf(
        ParticleParmDesc("view", 0, ""),
        ParticleParmDesc("aimed", 2, ""),
        ParticleParmDesc("x", 0, ""),
        ParticleParmDesc("y", 0, ""),
        ParticleParmDesc("z", 0, "")
    )

    enum class prtCustomPth_t {
        PPATH_STANDARD,
        PPATH_HELIX,  // ( sizeX sizeY sizeZ radialSpeed climbSpeed )
        PPATH_FLIES,
        PPATH_ORBIT,
        PPATH_DRIP
    }

    enum class prtDirection_t {
        PDIR_CONE,  // parm0 is the solid cone angle
        PDIR_OUTWARD // direction is relative to offset from origin, parm0 is an upward bias
    }

    enum class prtDistribution_t {
        PDIST_RECT,  // ( sizeX sizeY sizeZ )
        PDIST_CYLINDER,  // ( sizeX sizeY sizeZ )
        PDIST_SPHERE // ( sizeX sizeY sizeZ ringFraction )
        // a ringFraction of zero allows the entire sphere, 0.9 would only
        // allow the outer 10% of the sphere
    }

    enum class prtOrientation_t {
        POR_VIEW,
        POR_AIMED,  // angle and aspect are disregarded
        POR_X,
        POR_Y,
        POR_Z
    }

    /*
     ===============================================================================

     idDeclParticle

     ===============================================================================
     */
    class ParticleParmDesc(val name: String, var count: Int, val desc: String)

    /*
     ====================================================================================

     idParticleParm

     ====================================================================================
     */
    class idParticleParm {
        var from: Float
        var table: idDeclTable? = null
        var to = 0.0f
        fun Eval(frac: Float, rand: idRandom): Float {
            return if (table != null) {
                table!!.TableLookup(frac)
            } else from + frac * (to - from)
        }

        @Throws(idException::class)
        fun Integrate(frac: Float, rand: idRandom): Float {
            if (table != null) {
                Common.common.Printf("idParticleParm::Integrate: can't integrate tables\n")
                return 0.0f
            }
            return (from + frac * (to - from) * 0.5f) * frac
        }

        //
        //
        init {
            from = to
        }
    }

    class particleGen_t {
        //
        //

        var age // in seconds, calculated as fraction * stage->particleLife
                = 0.0f


        var animationFrameFrac // set by ParticleTexCoords, used to make the cross faded version
                = 0.0f


        val axis: idMat3 = idMat3()


        var frac // 0.0f to 1.0f
                = 0.0f


        var index // particle number in the system
                = 0


        val origin // dynamic smoke particles can have individual origins and axis
                : idVec3 = idVec3()


        var originalRandom // needed so aimed particles can reset the random for another origin calculation
                : idRandom = idRandom()


        var random: idRandom = idRandom()


        var renderEnt // for shaderParms, etc
                : renderEntity_s = renderEntity_s()


        var renderView: renderView_s = renderView_s()

    }

    //
    // single particle stage
    //
    class idParticleStage {
        //
        var animationFrames // if > 1, subdivide the texture S axis into frames and crossfade
                : Int
        var animationRate // frames per second
                : Float
        var aspect // greater than 1 makes the T axis longer
                : idParticleParm = idParticleParm()

        //

        val bounds // derived
                : idBounds = idBounds()

        //-----------------------------------
        //
        var boundsExpansion // user tweak to fix poorly calculated bounds
                : Float

        //
        val color: idVec4 = idVec4()
        var customPathParms: FloatArray = FloatArray(8)

        //
        //------------------------------	  // custom path will completely replace the standard path calculations
        //
        var customPathType // use custom C code routines for determining the origin
                : prtCustomPth_t = prtCustomPth_t.PPATH_STANDARD

        // on a per stage basis
        //

        var cycleMsec // ( particleLife + deadTime ) in msec
                = 0


        var cycles // allows things to oneShot ( 1 cycle ) or run for a set number of cycles
                = 0.0f


        var deadTime // time after particleLife before respawning
                = 0.0f


        var directionParms: FloatArray = FloatArray(4)

        //

        var directionType: prtDirection_t = prtDirection_t.PDIR_CONE


        var distributionParms: FloatArray = FloatArray(4)

        //
        //-------------------------------	  // standard path parms
        //
        var distributionType: prtDistribution_t = prtDistribution_t.PDIST_RECT
        var entityColor // force color from render entity ( fadeColor is still valid )
                : Boolean
        val fadeColor // either 0 0 0 0 for additive, or 1 1 1 0 for blended materials
                : idVec4 = idVec4()
        var fadeInFraction // in 0.0f to 1.0f range
                : Float


        var fadeIndexFraction // in 0.0f to 1.0f range, causes later index smokes to be more faded
                : Float


        var fadeOutFraction // in 0.0f to 1.0f range
                : Float


        var gravity // can be negative to float up
                : Float

        //

        var hidden // for editor use
                : Boolean

        //
        var initialAngle // in degrees, random angle is used if zero ( default )
                : Float


        var material: Material.idMaterial? = null

        //
        //--------------------------------
        //
        val offset // offset from origin to spawn all particles, also applies to customPath
                : idVec3 = idVec3()

        //
        var orientation // view, aimed, or axis fixed
                : prtOrientation_t = prtOrientation_t.POR_VIEW
        var orientationParms: FloatArray = FloatArray(4)


        var particleLife // total seconds of life for each particle
                = 0.0f
        var randomDistribution // randomly orient the quad on emission ( defaults to true )
                : Boolean
        var rotationSpeed // half the particles will have negative rotation speeds
                : idParticleParm = idParticleParm()

        //

        var size: idParticleParm = idParticleParm()

        //

        var spawnBunching // 0.0f = all come out at first instant, 1.0f = evenly spaced over cycle time
                = 0.0f

        //

        var speed: idParticleParm = idParticleParm()


        var timeOffset // time offset from system start for the first particle to spawn
                = 0.0f

        //

        var totalParticles // total number of particles, although some may be invisible at a given time
                = 0


        var worldGravity // apply gravity in world space
                : Boolean

        //	virtual					~idParticleStage( void ) {}
        //
        /*
         ================
         idParticleStage::Default

         Sets the stage to a default state
         ================
         */
        @Throws(idException::class)
        fun Default() {
            material = DeclManager.declManager.FindMaterial("_default")
            totalParticles = 100
            spawnBunching = 1.0f
            particleLife = 1.5f
            timeOffset = 0.0f
            deadTime = 0.0f
            distributionType = prtDistribution_t.PDIST_RECT
            distributionParms[0] = 8.0f
            distributionParms[1] = 8.0f
            distributionParms[2] = 8.0f
            distributionParms[3] = 0.0f
            directionType = prtDirection_t.PDIR_CONE
            directionParms[0] = 90.0f
            directionParms[1] = 0.0f
            directionParms[2] = 0.0f
            directionParms[3] = 0.0f
            orientation = prtOrientation_t.POR_VIEW
            orientationParms[0] = 0.0f
            orientationParms[1] = 0.0f
            orientationParms[2] = 0.0f
            orientationParms[3] = 0.0f
            speed.from = 150.0f
            speed.to = 150.0f
            speed.table = null
            gravity = 1.0f
            worldGravity = false
            customPathType = prtCustomPth_t.PPATH_STANDARD
            customPathParms[0] = 0.0f
            customPathParms[1] = 0.0f
            customPathParms[2] = 0.0f
            customPathParms[3] = 0.0f
            customPathParms[4] = 0.0f
            customPathParms[5] = 0.0f
            customPathParms[6] = 0.0f
            customPathParms[7] = 0.0f
            offset.Zero()
            animationFrames = 0
            animationRate = 0.0f
            initialAngle = 0.0f
            rotationSpeed.from = 0.0f
            rotationSpeed.to = 0.0f
            rotationSpeed.table = null
            size.from = 4.0f
            size.to = 4.0f
            size.table = null
            aspect.from = 1.0f
            aspect.to = 1.0f
            aspect.table = null
            color.x = 1.0f
            color.y = 1.0f
            color.z = 1.0f
            color.w = 1.0f
            fadeColor.x = 0.0f
            fadeColor.y = 0.0f
            fadeColor.z = 0.0f
            fadeColor.w = 0.0f
            fadeInFraction = 0.1f
            fadeOutFraction = 0.25f
            fadeIndexFraction = 0.0f
            boundsExpansion = 0.0f
            randomDistribution = true
            entityColor = false
            cycleMsec = ((particleLife + deadTime) * 1000).toInt()
        }

        /*
         ================
         idParticleStage::NumQuadsPerParticle

         includes trails and cross faded animations
         ================
         */
        fun NumQuadsPerParticle(): Int {  // includes trails and cross faded animations
            var count = 1
            if (orientation == prtOrientation_t.POR_AIMED) {
                val trails = idMath.Ftoi(orientationParms[0])
                // each trail stage will add an extra quad
                count *= 1 + trails
            }

            // if we are doing strip-animation, we need to double the number and cross fade them
            if (animationFrames > 1) {
                count *= 2
            }
            return count
        }

        /*
         ================
         idParticleStage::CreateParticle

         Returns 0 if no particle is created because it is completely faded out
         Returns 4 if a normal quad is created
         Returns 8 if two cross faded quads are created

         Vertex order is:

         0 1
         2 3
         ================
         */
        // returns the number of verts created, which will range from 0 to 4*NumQuadsPerParticle()
        @Throws(idException::class)
        fun CreateParticle(g: particleGen_t, verts: Array<idDrawVert>): Int {
            val origin = idVec3()

            verts[0].Clear()
            verts[1].Clear()
            verts[2].Clear()
            verts[3].Clear()

            ParticleColors(g, verts)

            // if we are completely faded out, kill the particle
            if (verts[0].color[0].toInt() == 0 && verts[0].color[1].toInt() == 0 && verts[0].color[2].toInt() == 0 && verts[0].color[3].toInt() == 0) {
                return 0
            }
            ParticleOrigin(g, origin)
            ParticleTexCoords(g, verts)
            val numVerts = ParticleVerts(g, origin, verts)
            if (animationFrames <= 1) {
                return numVerts
            }

            // if we are doing strip-animation, we need to double the quad and cross fade it
            val width = 1.0f / animationFrames
            val frac = g.animationFrameFrac
            val iFrac = 1.0f - frac
            // FIX: color bytes are stored as signed (Kotlin Byte), but C++ uses unsigned char (0-255).
            // Must mask with 0xFF before multiplying to avoid sign-extension producing negative results.
            for (i in 0 until numVerts) {
                verts[numVerts + i].set(verts[i])
                verts[numVerts + i].st.x += width
                verts[numVerts + i].color[0] = ((verts[numVerts + i].color[0].toInt() and 0xFF) * frac).toInt().toByte()
                verts[numVerts + i].color[1] = ((verts[numVerts + i].color[1].toInt() and 0xFF) * frac).toInt().toByte()
                verts[numVerts + i].color[2] = ((verts[numVerts + i].color[2].toInt() and 0xFF) * frac).toInt().toByte()
                verts[numVerts + i].color[3] = ((verts[numVerts + i].color[3].toInt() and 0xFF) * frac).toInt().toByte()
                verts[i].color[0] = ((verts[i].color[0].toInt() and 0xFF) * iFrac).toInt().toByte()
                verts[i].color[1] = ((verts[i].color[1].toInt() and 0xFF) * iFrac).toInt().toByte()
                verts[i].color[2] = ((verts[i].color[2].toInt() and 0xFF) * iFrac).toInt().toByte()
                verts[i].color[3] = ((verts[i].color[3].toInt() and 0xFF) * iFrac).toInt().toByte()
            }
            return numVerts * 2
        }

        @Throws(idException::class)
        fun ParticleOrigin(g: particleGen_t, origin: idVec3) {
            if (customPathType == prtCustomPth_t.PPATH_STANDARD) {
                //
                // find intial origin distribution
                //
                var radiusSqr: Float
                var angle1: Float
                val angle2: Float
                when (distributionType) {
                    prtDistribution_t.PDIST_RECT -> {
                        // ( sizeX sizeY sizeZ )
                        origin[0] = (if (randomDistribution) g.random.CRandomFloat() else 1.0f) * distributionParms[0]
                        origin[1] = (if (randomDistribution) g.random.CRandomFloat() else 1.0f) * distributionParms[1]
                        origin[2] = (if (randomDistribution) g.random.CRandomFloat() else 1.0f) * distributionParms[2]
                    }

                    prtDistribution_t.PDIST_CYLINDER -> {
                        // ( sizeX sizeY sizeZ ringFraction )
                        angle1 = (if (randomDistribution) g.random.CRandomFloat() else 1.0f) * idMath.TWO_PI
                        val origin2 = CFloat()
                        val origin3 = CFloat()
                        idMath.SinCos16(angle1, origin2, origin3)
                        origin[0] = origin2._val
                        origin[1] = origin3._val
                        origin[2] = if (randomDistribution) g.random.CRandomFloat() else 1.0f

                        // reproject points that are inside the ringFraction to the outer band
                        if (distributionParms[3] > 0.0f) {
                            radiusSqr = origin[0] * origin[0] + origin[1] * origin[1]
                            if (radiusSqr < distributionParms[3] * distributionParms[3]) {
                                // if we are inside the inner reject zone, rescale to put it out into the good zone
                                val f = (sqrt(radiusSqr) / distributionParms[3]).toFloat()
                                val invf = 1.0f / f
                                val newRadius = distributionParms[3] + f * (1.0f - distributionParms[3])
                                val rescale = invf * newRadius
                                origin.timesAssign(0, rescale)
                                origin.timesAssign(1, rescale)
                            }
                        }
                        origin.timesAssign(0, distributionParms[0])
                        origin.timesAssign(1, distributionParms[1])
                        origin.timesAssign(2, distributionParms[2])
                    }

                    prtDistribution_t.PDIST_SPHERE -> {
                        // ( sizeX sizeY sizeZ ringFraction )
                        // iterating with rejection is the only way to get an even distribution over a sphere
                        if (randomDistribution) {
                            do {
                                origin[0] = g.random.CRandomFloat()
                                origin[1] = g.random.CRandomFloat()
                                origin[2] = g.random.CRandomFloat()
                                radiusSqr =
                                    origin[0] * origin[0] + origin[1] * origin[1] + origin[2] * origin[2]
                            } while (radiusSqr > 1.0f)
                        } else {
                            origin.set(1.0f, 1.0f, 1.0f)
                            radiusSqr = 3.0f
                        }
                        if (distributionParms[3] > 0.0f) {
                            // we could iterate until we got something that also satisfied ringFraction,
                            // but for narrow rings that could be a lot of work, so reproject inside points instead
                            if (radiusSqr < distributionParms[3] * distributionParms[3]) {
                                // if we are inside the inner reject zone, rescale to put it out into the good zone
                                val f = (sqrt(radiusSqr) / distributionParms[3]).toFloat()
                                val invf = 1.0f / f
                                val newRadius = distributionParms[3] + f * (1.0f - distributionParms[3])
                                val rescale = invf * newRadius
                                origin.timesAssign(rescale)
                            }
                        }
                        origin.timesAssign(0, distributionParms[0])
                        origin.timesAssign(1, distributionParms[1])
                        origin.timesAssign(2, distributionParms[2])
                    }
                }

                // offset will effect all particle origin types
                // add this before the velocity and gravity additions
                origin.plusAssign(offset)

                //
                // add the velocity over time
                //
                val dir = idVec3()
                when (directionType) {
                    prtDirection_t.PDIR_CONE -> {

                        // angle is the full angle, so 360 degrees is any spherical direction
                        angle1 = g.random.CRandomFloat() * directionParms[0] * idMath.M_DEG2RAD
                        angle2 = g.random.CRandomFloat() * idMath.PI
                        val s1 = CFloat()
                        val s2 = CFloat()
                        val c1 = CFloat()
                        val c2 = CFloat()
                        idMath.SinCos16(angle1, s1, c1)
                        idMath.SinCos16(angle2, s2, c2)
                        dir[0] = s1._val * c2._val
                        dir[1] = s1._val * s2._val
                        dir[2] = c1._val
                    }

                    prtDirection_t.PDIR_OUTWARD -> {
                        dir.set(origin)
                        dir.Normalize()
                        dir.plusAssign(2, directionParms[0])
                    }

                    else -> {
                        Common.common.Error("idParticleStage::ParticleOrigin: bad direction")
                        return
                    }
                }

                // add speed
                val iSpeed = speed.Integrate(g.frac, g.random)
                origin.plusAssign(dir.times(iSpeed).times(particleLife))
            } else {
                //
                // custom paths completely override both the origin and velocity calculations, but still
                // use the standard gravity
                //
                val angle1: Float
                val angle2: Float
                val speed1: Float
                val speed2: Float
                when (customPathType) {
                    prtCustomPth_t.PPATH_HELIX -> {
                        // ( sizeX sizeY sizeZ radialSpeed axialSpeed )
                        speed1 = g.random.CRandomFloat()
                        speed2 = g.random.CRandomFloat()
                        angle1 = g.random.RandomFloat() * idMath.TWO_PI + customPathParms[3] * speed1 * g.age
                        val s1 = CFloat()
                        val c1 = CFloat()
                        idMath.SinCos16(angle1, s1, c1)
                        origin[0] = c1._val * customPathParms[0]
                        origin[1] = s1._val * customPathParms[1]
                        origin[2] = g.random.RandomFloat() * customPathParms[2] + customPathParms[4] * speed2 * g.age
                    }

                    prtCustomPth_t.PPATH_FLIES -> {
                        // ( radialSpeed axialSpeed size )
                        speed1 = idMath.ClampFloat(0.4f, 1.0f, g.random.CRandomFloat())
                        //				speed2 = idMath.ClampFloat( 0.4, 1.0f, g.random.CRandomFloat() );
                        angle1 = g.random.RandomFloat() * idMath.PI * 2 + customPathParms[0] * speed1 * g.age
                        angle2 = g.random.RandomFloat() * idMath.PI * 2 + customPathParms[1] * speed1 * g.age
                        val s1 = CFloat()
                        val s2 = CFloat()
                        val c1 = CFloat()
                        val c2 = CFloat()
                        idMath.SinCos16(angle1, s1, c1)
                        idMath.SinCos16(angle2, s2, c2)
                        origin[0] = c1._val * c2._val
                        origin[1] = s1._val * c2._val
                        origin[2] = -s2._val
                        // FIX: was origin.times(customPathParms[2]) which returns a new vector and discards it; must modify in-place
                        origin.timesAssign(customPathParms[2])
                    }

                    prtCustomPth_t.PPATH_ORBIT -> {
                        // ( radius speed axis )
                        angle1 = g.random.RandomFloat() * idMath.TWO_PI + customPathParms[1] * g.age
                        val s1 = CFloat()
                        val c1 = CFloat()
                        idMath.SinCos16(angle1, s1, c1)
                        origin[0] = c1._val * customPathParms[0]
                        origin[1] = s1._val * customPathParms[0]
                        origin.ProjectSelfOntoSphere(customPathParms[0])
                    }

                    prtCustomPth_t.PPATH_DRIP -> {
                        // ( speed )
                        origin[0] = 0.0f
                        origin[1] = 0.0f
                        origin[2] = -(g.age * customPathParms[0])
                    }

                    else -> {
                        Common.common.Error("idParticleStage.ParticleOrigin: bad customPathType")
                    }
                }
                origin.plusAssign(offset)
            }

            // adjust for the per-particle smoke offset
            origin.timesAssign(g.axis)
            origin.plusAssign(g.origin)

            // add gravity after adjusting for axis
            if (worldGravity) {
                val gra = idVec3(0.0f, 0.0f, -gravity)
                gra.timesAssign(g.renderEnt.axis.Transpose())
                origin.plusAssign(gra.times(g.age * g.age))
            } else {
                origin.minusAssign(2, gravity * g.age * g.age)
            }
        }

        @Throws(idException::class)
        fun ParticleVerts(g: particleGen_t, origin: idVec3, verts: Array<idDrawVert>): Int {
            val psize = size.Eval(g.frac, g.random)
            val paspect = aspect.Eval(g.frac, g.random)
            var height = psize * paspect
            val left = idVec3()
            val up = idVec3()
            if (orientation == prtOrientation_t.POR_AIMED) {
                // reset the values to an earlier time to get a previous origin
                val currentRandom = idRandom(g.random)
                val currentAge = g.age
                val currentFrac = g.frac
                //		idDrawVert []verts_p = verts[verts_p;
                var verts_p = 0
                val stepOrigin = idVec3(origin)
                val stepLeft = idVec3()
                val numTrails = idMath.Ftoi(orientationParms[0])
                var trailTime = orientationParms[1]
                if (trailTime == 0.0f) {
                    trailTime = 0.5f
                }
                height = 1.0f / (1 + numTrails)
                var t = 0.0f
                for (i in 0..numTrails) {
                    g.random = idRandom(g.originalRandom)
                    g.age = currentAge - (i + 1) * trailTime / (numTrails + 1) // time to back up
                    g.frac = g.age / particleLife
                    val oldOrigin = idVec3()
                    ParticleOrigin(g, oldOrigin)
                    up.set(stepOrigin.minus(oldOrigin)) // along the direction of travel
                    val forwardDir = idVec3()
                    g.renderEnt.axis.ProjectVector(g.renderView.viewaxis[0], forwardDir)
                    up.minusAssign(forwardDir.times(up.times(forwardDir)))
                    up.Normalize()
                    left.set(up.Cross(forwardDir))
                    left.timesAssign(psize)
                    verts[verts_p + 0].set(verts[0])
                    verts[verts_p + 1].set(verts[1])
                    verts[verts_p + 2].set(verts[2])
                    verts[verts_p + 3].set(verts[3])
                    if (i == 0) {
                        verts[verts_p + 0].xyz.set(stepOrigin.minus(left))
                        verts[verts_p + 1].xyz.set(stepOrigin.plus(left))
                    } else {
                        verts[verts_p + 0].xyz.set(stepOrigin.minus(stepLeft))
                        verts[verts_p + 1].xyz.set(stepOrigin.plus(stepLeft))
                    }
                    verts[verts_p + 2].xyz.set(oldOrigin.minus(left))
                    verts[verts_p + 3].xyz.set(oldOrigin.plus(left))

                    // modify texcoords
                    verts[verts_p + 0].st.x = verts[0].st.x
                    verts[verts_p + 0].st.y = t
                    verts[verts_p + 1].st.x = verts[1].st.x
                    verts[verts_p + 1].st.y = t
                    verts[verts_p + 2].st.x = verts[2].st.x
                    verts[verts_p + 2].st.y = t + height
                    verts[verts_p + 3].st.x = verts[3].st.x
                    verts[verts_p + 3].st.y = t + height
                    t += height
                    verts_p += 4
                    stepOrigin.set(oldOrigin)
                    stepLeft.set(left)
                }
                g.random = idRandom(currentRandom)
                g.age = currentAge
                g.frac = currentFrac
                return 4 * (numTrails + 1)
            }

            //
            // constant rotation 
            //
            var angle: Float
            angle = if (initialAngle != 0.0f) initialAngle else 360 * g.random.RandomFloat()
            val angleMove = rotationSpeed.Integrate(g.frac, g.random) * particleLife
            // have hald the particles rotate each way
            if (g.index and 1 != 0) {
                angle += angleMove
            } else {
                angle -= angleMove
            }
            angle = angle / 180 * idMath.PI
            val c = idMath.Cos16(angle)
            val s = idMath.Sin16(angle)
            if (orientation == prtOrientation_t.POR_Z) {
                // oriented in entity space
                left.x = s
                left.y = c
                left.z = 0.0f
                up.x = c
                up.y = -s
                up.z = 0.0f
            } else if (orientation == prtOrientation_t.POR_X) {
                // oriented in entity space
                left.x = 0.0f
                left.y = c
                left.z = s
                up.x = 0.0f
                up.y = -s
                up.z = c
            } else if (orientation == prtOrientation_t.POR_Y) {
                // oriented in entity space
                left.x = c
                left.y = 0.0f
                left.z = s
                up.x = -s
                up.y = 0.0f
                up.z = c
            } else {
                // oriented in viewer space
                val entityLeft = idVec3()
                val entityUp = idVec3()
                g.renderEnt.axis.ProjectVector(g.renderView.viewaxis[1], entityLeft)
                g.renderEnt.axis.ProjectVector(g.renderView.viewaxis[2], entityUp)
                left.set(entityLeft.times(c).plus(entityUp.times(s)))
                up.set(entityUp.times(c).minus(entityLeft.times(s)))
            }
            left.timesAssign(psize)
            up.timesAssign(height)
            verts[0].xyz.set(origin.minus(left).plus(up))
            verts[1].xyz.set(origin.plus(left).plus(up))
            verts[2].xyz.set(origin.minus(left).minus(up))
            verts[3].xyz.set(origin.plus(left).minus(up))
            return 4
        }

        fun ParticleTexCoords(g: particleGen_t, verts: Array<idDrawVert>) {
            val s: Float
            val width: Float
            val t: Float
            val height: Float
            if (animationFrames > 1) {
                width = 1.0f / animationFrames
                val floatFrame: Float
                floatFrame = if (animationRate != 0.0f) {
                    // explicit, cycling animation
                    g.age * animationRate
                } else {
                    // single animation cycle over the life of the particle
                    g.frac * animationFrames
                }
                val intFrame = floatFrame.toInt()
                g.animationFrameFrac = floatFrame - intFrame
                s = width * intFrame
            } else {
                s = 0.0f
                width = 1.0f
            }
            t = 0.0f
            height = 1.0f
            verts[0].st[0] = s
            verts[0].st[1] = t
            verts[1].st[0] = s + width
            verts[1].st[1] = t
            verts[2].st[0] = s
            verts[2].st[1] = t + height
            verts[3].st[0] = s + width
            verts[3].st[1] = t + height
        }

        fun ParticleColors(g: particleGen_t, verts: Array<idDrawVert>) {
            var fadeFraction = 1.0f

            // most particles fade in at the beginning and fade out at the end
            if (g.frac < fadeInFraction) {
                fadeFraction *= g.frac / fadeInFraction
            }
            if (1.0f - g.frac < fadeOutFraction) {
                fadeFraction *= (1.0f - g.frac) / fadeOutFraction
            }

            // individual gun smoke particles get more and more faded as the
            // cycle goes on (note that totalParticles won't be correct for a surface-particle deform)
            if (fadeIndexFraction != 0.0f) {
                val indexFrac = (totalParticles - g.index) / totalParticles.toFloat()
                if (indexFrac < fadeIndexFraction) {
                    fadeFraction *= indexFrac / fadeIndexFraction
                }
            }
            for (i in 0..3) {
                val fcolor =
                    (if (entityColor) g.renderEnt.shaderParms[i] else color[i]) * fadeFraction + fadeColor[i] * (1.0f - fadeFraction)
                var icolor = idMath.FtoiFast(fcolor * 255.0f)
                if (icolor < 0) {
                    icolor = 0
                } else if (icolor > 255) {
                    icolor = 255
                }
                verts[3].color[i] = icolor.toByte()
                verts[2].color[i] = verts[3].color[i]
                verts[1].color[i] = verts[2].color[i]
                verts[0].color[i] = verts[1].color[i]
            }
        }

        //
        fun GetCustomPathName(): String {
            val index = if (customPathType.ordinal < CustomParticleCount) customPathType.ordinal else 0
            return ParticleCustomDesc[index].name
        }

        fun GetCustomPathDesc(): String {
            val index = if (customPathType.ordinal < CustomParticleCount) customPathType.ordinal else 0
            return ParticleCustomDesc[index].desc
        }

        fun NumCustomPathParms(): Int {
            val index = if (customPathType.ordinal < CustomParticleCount) customPathType.ordinal else 0
            return ParticleCustomDesc[index].count
        }

        fun SetCustomPathType(p: String) {
            customPathType = prtCustomPth_t.PPATH_STANDARD
            val values: Array<prtCustomPth_t> = prtCustomPth_t.values()
            var i = 0
            while (i < CustomParticleCount && i < values.size) {
                if (idStr.Icmp(p, ParticleCustomDesc[i].name) == 0) {
                    customPathType =  /*static_cast<prtCustomPth_t>*/values[i]
                    break
                }
                i++
            }
        }

        //public	void					operator=( const idParticleStage &src );
        fun oSet(src: idParticleStage) {
            material = src.material
            totalParticles = src.totalParticles
            cycles = src.cycles
            cycleMsec = src.cycleMsec
            spawnBunching = src.spawnBunching
            particleLife = src.particleLife
            timeOffset = src.timeOffset
            deadTime = src.deadTime
            distributionType = src.distributionType
            distributionParms[0] = src.distributionParms[0]
            distributionParms[1] = src.distributionParms[1]
            distributionParms[2] = src.distributionParms[2]
            distributionParms[3] = src.distributionParms[3]
            directionType = src.directionType
            directionParms[0] = src.directionParms[0]
            directionParms[1] = src.directionParms[1]
            directionParms[2] = src.directionParms[2]
            directionParms[3] = src.directionParms[3]
            // FIX: was 'speed = src.speed' (reference copy, causes aliasing between stages)
            // C++ operator= copies the struct by value; replicate field-by-field
            speed.from = src.speed.from
            speed.to = src.speed.to
            speed.table = src.speed.table
            gravity = src.gravity
            worldGravity = src.worldGravity
            randomDistribution = src.randomDistribution
            entityColor = src.entityColor
            customPathType = src.customPathType
            customPathParms[0] = src.customPathParms[0]
            customPathParms[1] = src.customPathParms[1]
            customPathParms[2] = src.customPathParms[2]
            customPathParms[3] = src.customPathParms[3]
            customPathParms[4] = src.customPathParms[4]
            customPathParms[5] = src.customPathParms[5]
            customPathParms[6] = src.customPathParms[6]
            customPathParms[7] = src.customPathParms[7]
            offset.set(src.offset)
            animationFrames = src.animationFrames
            animationRate = src.animationRate
            initialAngle = src.initialAngle
            // FIX: was 'rotationSpeed = src.rotationSpeed' (reference copy)
            rotationSpeed.from = src.rotationSpeed.from
            rotationSpeed.to = src.rotationSpeed.to
            rotationSpeed.table = src.rotationSpeed.table
            orientation = src.orientation
            orientationParms[0] = src.orientationParms[0]
            orientationParms[1] = src.orientationParms[1]
            orientationParms[2] = src.orientationParms[2]
            orientationParms[3] = src.orientationParms[3]
            // FIX: was 'size = src.size' / 'aspect = src.aspect' (reference copies)
            size.from = src.size.from
            size.to = src.size.to
            size.table = src.size.table
            aspect.from = src.aspect.from
            aspect.to = src.aspect.to
            aspect.table = src.aspect.table
            color.set(src.color)
            fadeColor.set(src.fadeColor)
            fadeInFraction = src.fadeInFraction
            fadeOutFraction = src.fadeOutFraction
            fadeIndexFraction = src.fadeIndexFraction
            hidden = src.hidden
            boundsExpansion = src.boundsExpansion
            bounds.set(src.bounds)
        }

        //
        //
        init {
            distributionType = prtDistribution_t.PDIST_RECT
            distributionParms[3] = 0.0f
            distributionParms[2] = distributionParms[3]
            distributionParms[1] = distributionParms[2]
            distributionParms[0] = distributionParms[1]
            directionType = prtDirection_t.PDIR_CONE
            directionParms[3] = 0.0f
            directionParms[2] = directionParms[3]
            directionParms[1] = directionParms[2]
            directionParms[0] = directionParms[1]
            speed = idParticleParm()
            gravity = 0.0f
            worldGravity = false
            customPathType = prtCustomPth_t.PPATH_STANDARD
            customPathParms[3] = 0.0f
            customPathParms[2] = customPathParms[3]
            customPathParms[1] = customPathParms[2]
            customPathParms[0] = customPathParms[1]
            customPathParms[7] = 0.0f
            customPathParms[6] = customPathParms[7]
            customPathParms[5] = customPathParms[6]
            customPathParms[4] = customPathParms[5]
            animationFrames = 0
            animationRate = 0.0f
            randomDistribution = true
            entityColor = false
            initialAngle = 0.0f
            rotationSpeed = idParticleParm()
            orientation = prtOrientation_t.POR_VIEW
            orientationParms[3] = 0.0f
            orientationParms[2] = orientationParms[3]
            orientationParms[1] = orientationParms[2]
            orientationParms[0] = orientationParms[1]
            size = idParticleParm()
            aspect = idParticleParm()
            fadeInFraction = 0.0f
            fadeOutFraction = 0.0f
            fadeIndexFraction = 0.0f
            hidden = false
            boundsExpansion = 0.0f
            bounds.Clear()
        }
    }

    //
    // group of particle stages
    //
    class idDeclParticle : idDecl() {

        val bounds: idBounds = idBounds()


        var depthHack = 0.0f


        val stages: idList<idParticleStage> = idList()
        override fun DefaultDefinition(): String {
            // NOTE: original C++ uses "1.0" (not "1.0f") — match exactly
            return """{
	{
		material	_default
		count	20
		time		1.0
	}
}"""
        }

        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            val token = idToken()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")
            depthHack = 0.0f
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (0 == token.Icmp("}")) {
                    break
                }
                if (0 == token.Icmp("{")) {
                    val stage = ParseParticleStage(src)
                    if (null == stage) {
                        src.Warning("Particle stage parse failed")
                        MakeDefault()
                        return false
                    }
                    stages.Append(stage)
                    continue
                }
                if (0 == token.Icmp("depthHack")) {
                    depthHack = src.ParseFloat()
                    continue
                }
                src.Warning("bad token %s", token.toString())
                MakeDefault()
                return false
            }

            //
            // calculate the bounds
            //
            bounds.Clear()
            for (i in 0 until stages.Num()) {
                GetStageBounds(stages[i])
                bounds.AddBounds(stages[i].bounds)
            }
            if (bounds.GetVolume() <= 0.1f) {
                bounds.set(idBounds(vec3_origin).Expand(8.0f))
            }
            return true
        }

        override fun FreeData() {
            stages.DeleteContents(true)
        }


        @Throws(idException::class)
        fun Save(fileName: String? = null): Boolean {
            RebuildTextSource()
            if (fileName != null) {
                DeclManager.declManager.CreateNewDecl(declType_t.DECL_PARTICLE, GetName(), fileName)
            }
            ReplaceSourceFileText()
            return true
        }

        private fun RebuildTextSource(): Boolean {
            val f = idFile_Memory()
            f.WriteFloatString(
                """

/*
	Generated by the Particle Editor.
	To use the particle editor, launch the game and type 'editParticles' on the console.
*/
"""
            )
            f.WriteFloatString("particle %s {\n", GetName())
            if (depthHack != 0.0f) {
                f.WriteFloatString("\tdepthHack\t%f\n", depthHack)
            }
            for (i in 0 until stages.Num()) {
                WriteStage(f, stages[i])
            }
            f.WriteFloatString("}")
            SetText(String(f.GetDataPtr().array()))
            return true
        }

        @Throws(idException::class)
        private fun GetStageBounds(stage: idParticleStage) {
            stage.bounds.Clear()

            // this isn't absolutely guaranteed, but it should be close
            val g = particleGen_t()
            val renderEntity = renderEntity_s() //memset( &renderEntity, 0, sizeof( renderEntity ) );
            renderEntity.axis.set(idMat3.getMat3_identity())
            val renderView = renderView_s() //memset( &renderView, 0, sizeof( renderView ) );
            renderView.viewaxis.set(idMat3.getMat3_identity())
            g.renderEnt = renderEntity
            g.renderView = renderView
            g.origin.set(idVec3())
            g.axis.set(idMat3.getMat3_identity())
            val steppingRandom = idRandom()
            steppingRandom.SetSeed(0)

            // just step through a lot of possible particles as a representative sampling
            for (i in 0 until 1000) {
                g.random = idRandom(idRandom(steppingRandom).also { g.originalRandom = it })
                val maxMsec = (stage.particleLife * 1000).toInt()
                var inCycleTime = 0
                while (inCycleTime < maxMsec) {


                    // make sure we get the very last tic, which may make up an extreme edge
                    if (inCycleTime + 16 > maxMsec) {
                        inCycleTime = maxMsec - 1
                    }
                    g.frac = inCycleTime.toFloat() / (stage.particleLife * 1000)
                    g.age = inCycleTime * 0.001f

                    // if the particle doesn't get drawn because it is faded out or beyond a kill region,
                    // don't increment the verts
                    val origin = idVec3()
                    stage.ParticleOrigin(g, origin)
                    stage.bounds.AddPoint(origin)
                    inCycleTime += 16
                }
            }

            // find the max size
            var maxSize = 0.0f
            var f = 0.0f
            while (f <= 1.0f) {
                var size = stage.size.Eval(f, steppingRandom)
                val aspect = stage.aspect.Eval(f, steppingRandom)
                if (aspect > 1) {
                    size *= aspect
                }
                if (size > maxSize) {
                    maxSize = size
                }
                f += 1.0f / 64
            }
            maxSize += 8.0f // just for good measure
            // users can specify a per-stage bounds expansion to handle odd cases
            stage.bounds.ExpandSelf(maxSize + stage.boundsExpansion)
        }

        @Throws(idException::class)
        private fun ParseParticleStage(src: idLexer): idParticleStage {
            val token = idToken()
            val stage = idParticleStage()
            stage.Default()
            while (true) {
                if (src.HadError()) {
                    break
                }
                if (!src.ReadToken(token)) {
                    break
                }
                if (0 == token.Icmp("}")) {
                    break
                }
                if (0 == token.Icmp("material")) {
                    src.ReadToken(token)
                    stage.material = DeclManager.declManager.FindMaterial(token)
                    continue
                }
                if (0 == token.Icmp("count")) {
                    stage.totalParticles = src.ParseInt()
                    continue
                }
                if (0 == token.Icmp("time")) {
                    stage.particleLife = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("cycles")) {
                    stage.cycles = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("timeOffset")) {
                    stage.timeOffset = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("deadTime")) {
                    stage.deadTime = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("randomDistribution")) {
                    stage.randomDistribution = src.ParseBool()
                    continue
                }
                if (0 == token.Icmp("bunching")) {
                    stage.spawnBunching = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("distribution")) {
                    src.ReadToken(token)
                    if (0 == token.Icmp("rect")) {
                        stage.distributionType = prtDistribution_t.PDIST_RECT
                    } else if (0 == token.Icmp("cylinder")) {
                        stage.distributionType = prtDistribution_t.PDIST_CYLINDER
                    } else if (0 == token.Icmp("sphere")) {
                        stage.distributionType = prtDistribution_t.PDIST_SPHERE
                    } else {
                        src.Error("bad distribution type: %s\n", token.toString())
                    }
                    ParseParms(src, stage.distributionParms, stage.distributionParms.size)
                    continue
                }
                if (0 == token.Icmp("direction")) {
                    src.ReadToken(token)
                    if (0 == token.Icmp("cone")) {
                        stage.directionType = prtDirection_t.PDIR_CONE
                    } else if (0 == token.Icmp("outward")) {
                        stage.directionType = prtDirection_t.PDIR_OUTWARD
                    } else {
                        src.Error("bad direction type: %s\n", token.toString())
                    }
                    ParseParms(src, stage.directionParms, stage.directionParms.size)
                    continue
                }
                if (0 == token.Icmp("orientation")) {
                    src.ReadToken(token)
                    if (0 == token.Icmp("view")) {
                        stage.orientation = prtOrientation_t.POR_VIEW
                    } else if (0 == token.Icmp("aimed")) {
                        stage.orientation = prtOrientation_t.POR_AIMED
                    } else if (0 == token.Icmp("x")) {
                        stage.orientation = prtOrientation_t.POR_X
                    } else if (0 == token.Icmp("y")) {
                        stage.orientation = prtOrientation_t.POR_Y
                    } else if (0 == token.Icmp("z")) {
                        stage.orientation = prtOrientation_t.POR_Z
                    } else {
                        src.Error("bad orientation type: %s\n", token.toString())
                    }
                    ParseParms(src, stage.orientationParms, stage.orientationParms.size)
                    continue
                }
                if (0 == token.Icmp("customPath")) {
                    src.ReadToken(token)
                    if (0 == token.Icmp("standard")) {
                        stage.customPathType = prtCustomPth_t.PPATH_STANDARD
                    } else if (0 == token.Icmp("helix")) {
                        stage.customPathType = prtCustomPth_t.PPATH_HELIX
                    } else if (0 == token.Icmp("flies")) {
                        stage.customPathType = prtCustomPth_t.PPATH_FLIES
                    } else if (0 == token.Icmp("spherical")) {
                        stage.customPathType = prtCustomPth_t.PPATH_ORBIT
                    } else {
                        src.Error("bad path type: %s\n", token.toString())
                    }
                    ParseParms(src, stage.customPathParms, stage.customPathParms.size)
                    continue
                }
                if (0 == token.Icmp("speed")) {
                    ParseParametric(src, stage.speed)
                    continue
                }
                if (0 == token.Icmp("rotation")) {
                    ParseParametric(src, stage.rotationSpeed)
                    continue
                }
                if (0 == token.Icmp("angle")) {
                    stage.initialAngle = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("entityColor")) {
                    stage.entityColor = src.ParseBool()
                    continue
                }
                if (0 == token.Icmp("size")) {
                    ParseParametric(src, stage.size)
                    continue
                }
                if (0 == token.Icmp("aspect")) {
                    ParseParametric(src, stage.aspect)
                    continue
                }
                if (0 == token.Icmp("fadeIn")) {
                    stage.fadeInFraction = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("fadeOut")) {
                    stage.fadeOutFraction = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("fadeIndex")) {
                    stage.fadeIndexFraction = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("color")) {
                    stage.color[0] = src.ParseFloat()
                    stage.color[1] = src.ParseFloat()
                    stage.color[2] = src.ParseFloat()
                    stage.color[3] = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("fadeColor")) {
                    stage.fadeColor[0] = src.ParseFloat()
                    stage.fadeColor[1] = src.ParseFloat()
                    stage.fadeColor[2] = src.ParseFloat()
                    stage.fadeColor[3] = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("offset")) {
                    stage.offset[0] = src.ParseFloat()
                    stage.offset[1] = src.ParseFloat()
                    stage.offset[2] = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("animationFrames")) {
                    stage.animationFrames = src.ParseInt()
                    continue
                }
                if (0 == token.Icmp("animationRate")) {
                    stage.animationRate = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("boundsExpansion")) {
                    stage.boundsExpansion = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("gravity")) {
                    src.ReadToken(token)
                    if (0 == token.Icmp("world")) {
                        stage.worldGravity = true
                    } else {
                        src.UnreadToken(token)
                    }
                    stage.gravity = src.ParseFloat()
                    continue
                }
                if (0 == token.Icmp("softeningRadius")) { // #3878 soft particles
                    Common.common.Warning(
                        "Particle %s from %s has stage with \"softeningRadius\" attribute, which is currently ignored (we soften all suitable particles)\n",
                        this.GetName(), src.GetFileName()
                    )
                    src.ParseFloat() // consume the value
                    continue
                }
                src.Error("unknown token %s\n", token.toString())
            }

            // derive values
            stage.cycleMsec = ((stage.particleLife + stage.deadTime) * 1000).toInt()
            return stage
        }

        /*
         ================
         idDeclParticle::ParseParms

         Parses a variable length list of parms on one line
         ================
         */
        @Throws(idException::class)
        private fun ParseParms(src: idLexer, parms: FloatArray, maxParms: Int) {
            val token = idToken()
            Arrays.fill(parms, 0, maxParms, 0.0f) //memset( parms, 0, maxParms * sizeof( *parms ) );
            var count = 0
            while (true) {
                if (!src.ReadTokenOnLine(token)) {
                    return
                }
                if (count == maxParms) {
                    src.Error("too many parms on line")
                    return
                }
                token.StripQuotes()
                parms[count] = TempDump.atof(token.toString())
                count++
            }
        }

        @Throws(idException::class)
        private fun ParseParametric(src: idLexer, parm: idParticleParm) {
            val token = idToken()
            parm.table = null
            parm.to = 0.0f
            parm.from = parm.to
            if (!src.ReadToken(token)) {
                src.Error("not enough parameters")
                return
            }
            if (token.IsNumeric()) {
                // can have a to + 2nd parm
                parm.to = TempDump.atof(token.toString())
                parm.from = parm.to
                if (src.ReadToken(token)) {
                    if (0 == token.Icmp("to")) {
                        if (!src.ReadToken(token)) {
                            src.Error("missing second parameter")
                            return
                        }
                        parm.to = TempDump.atof(token.toString())
                    } else {
                        src.UnreadToken(token)
                    }
                }
            } else {
                // table
                parm.table =  /*static_cast<const idDeclTable *>*/
                    DeclManager.declManager.FindType(declType_t.DECL_TABLE, token, false) as idDeclTable
            }
        }

        private fun WriteStage(f: idFile, stage: idParticleStage) {
            var i: Int
            f.WriteFloatString("\t{\n")
            f.WriteFloatString("\t\tcount\t\t\t\t%d\n", stage.totalParticles)
            f.WriteFloatString("\t\tmaterial\t\t\t%s\n", stage.material!!.GetName())
            if (stage.animationFrames != 0) {
                f.WriteFloatString("\t\tanimationFrames \t%d\n", stage.animationFrames)
            }
            if (stage.animationRate != 0.0f) {
                f.WriteFloatString("\t\tanimationRate \t\t%.3f\n", stage.animationRate)
            }
            f.WriteFloatString("\t\ttime\t\t\t\t%.3f\n", stage.particleLife)
            f.WriteFloatString("\t\tcycles\t\t\t\t%.3f\n", stage.cycles)
            if (stage.timeOffset != 0.0f) {
                f.WriteFloatString("\t\ttimeOffset\t\t\t%.3f\n", stage.timeOffset)
            }
            if (stage.deadTime != 0.0f) {
                f.WriteFloatString("\t\tdeadTime\t\t\t%.3f\n", stage.deadTime)
            }
            f.WriteFloatString("\t\tbunching\t\t\t%.3f\n", stage.spawnBunching)
            f.WriteFloatString(
                "\t\tdistribution\t\t%s ",
                ParticleDistributionDesc[stage.distributionType.ordinal].name
            )
            i = 0
            while (i < ParticleDistributionDesc[stage.distributionType.ordinal].count) {
                f.WriteFloatString("%.3f ", stage.distributionParms[i])
                i++
            }
            f.WriteFloatString("\n")
            f.WriteFloatString(
                "\t\tdirection\t\t\t%s ",
                ParticleDirectionDesc[stage.directionType.ordinal].name
            )
            i = 0
            while (i < ParticleDirectionDesc[stage.directionType.ordinal].count) {
                f.WriteFloatString("\"%.3f\" ", stage.directionParms[i])
                i++
            }
            f.WriteFloatString("\n")
            f.WriteFloatString(
                "\t\torientation\t\t\t%s ",
                ParticleOrientationDesc[stage.orientation.ordinal].name
            )
            i = 0
            while (i < ParticleOrientationDesc[stage.orientation.ordinal].count) {
                f.WriteFloatString("%.3f ", stage.orientationParms[i])
                i++
            }
            f.WriteFloatString("\n")
            if (stage.customPathType != prtCustomPth_t.PPATH_STANDARD) {
                f.WriteFloatString(
                    "\t\tcustomPath %s ",
                    ParticleCustomDesc[stage.customPathType.ordinal].name
                )
                i = 0
                while (i < ParticleCustomDesc[stage.customPathType.ordinal].count) {
                    f.WriteFloatString("%.3f ", stage.customPathParms[i])
                    i++
                }
                f.WriteFloatString("\n")
            }
            if (stage.entityColor) {
                f.WriteFloatString("\t\tentityColor\t\t\t1\n")
            }
            WriteParticleParm(f, stage.speed, "speed")
            WriteParticleParm(f, stage.size, "size")
            WriteParticleParm(f, stage.aspect, "aspect")
            if (stage.rotationSpeed.from != 0.0f) {
                WriteParticleParm(f, stage.rotationSpeed, "rotation")
            }
            if (stage.initialAngle != 0.0f) {
                f.WriteFloatString("\t\tangle\t\t\t\t%.3f\n", stage.initialAngle)
            }
            f.WriteFloatString("\t\trandomDistribution\t\t\t\t%d\n", if (stage.randomDistribution) 1 else 0)
            f.WriteFloatString("\t\tboundsExpansion\t\t\t\t%.3f\n", stage.boundsExpansion)
            f.WriteFloatString("\t\tfadeIn\t\t\t\t%.3f\n", stage.fadeInFraction)
            f.WriteFloatString("\t\tfadeOut\t\t\t\t%.3f\n", stage.fadeOutFraction)
            f.WriteFloatString("\t\tfadeIndex\t\t\t\t%.3f\n", stage.fadeIndexFraction)
            f.WriteFloatString(
                "\t\tcolor \t\t\t\t%.3f %.3f %.3f %.3f\n",
                stage.color.x,
                stage.color.y,
                stage.color.z,
                stage.color.w
            )
            f.WriteFloatString(
                "\t\tfadeColor \t\t\t%.3f %.3f %.3f %.3f\n",
                stage.fadeColor.x,
                stage.fadeColor.y,
                stage.fadeColor.z,
                stage.fadeColor.w
            )
            f.WriteFloatString("\t\toffset \t\t\t\t%.3f %.3f %.3f\n", stage.offset.x, stage.offset.y, stage.offset.z)
            f.WriteFloatString("\t\tgravity \t\t\t")
            if (stage.worldGravity) {
                f.WriteFloatString("world ")
            }
            f.WriteFloatString("%.3f\n", stage.gravity)
            f.WriteFloatString("\t}\n")
        }

        private fun WriteParticleParm(f: idFile, parm: idParticleParm, name: String) {
            f.WriteFloatString("\t\t%s\t\t\t\t ", name)
            if (parm.table != null) {
                f.WriteFloatString("%s\n", parm.table!!.GetName())
            } else {
                f.WriteFloatString("\"%.3f\" ", parm.from)
                if (parm.from == parm.to) {
                    f.WriteFloatString("\n")
                } else {
                    f.WriteFloatString(" to \"%.3f\"\n", parm.to)
                }
            }
        }

        fun oSet(idDeclParticle: idDeclParticle) {
            throw UnsupportedOperationException("Not supported yet.")
        }
    }
}