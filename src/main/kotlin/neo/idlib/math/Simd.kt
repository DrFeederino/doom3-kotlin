package neo.idlib.math

import neo.Game.Animation.idAnimBlend
import neo.Renderer.Model.dominantTri_s
import neo.Renderer.Model.shadowCache_s
import neo.TempDump
import neo.idlib.CmdArgs
import neo.idlib.containers.CFloat
import neo.idlib.containers.List
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.idlib.idLib
import neo.idlib.math.Matrix.idMatX
import neo.sys.sys_public
import java.nio.FloatBuffer
import java.util.*

var baseClocks: Long = 0
var generic: idSIMDProcessor? = idSIMD_Generic() // pointer to generic SIMD implementation
var SIMDProcessor: idSIMDProcessor? = generic
var processor: idSIMDProcessor? = null // pointer to SIMD processor

enum class speakerLabel {
    SPEAKER_LEFT, SPEAKER_RIGHT, SPEAKER_CENTER, SPEAKER_LFE, SPEAKER_BACKLEFT, SPEAKER_BACKRIGHT
}

/*
 ===============================================================================

 Single Instruction Multiple Data (SIMD)

 For optimal use data should be aligned on a 16 byte boundary.
 All idSIMDProcessor routines are thread safe.

 ===============================================================================
 */
object idSIMD {
    fun Init() {
        generic = idSIMD_Generic()
        generic?.cpuid = sys_public.CPUID_GENERIC
        processor = null
        SIMDProcessor = generic
    }

    fun Test_f(args: CmdArgs) {
        // Test functionality would be implemented here
        // This matches the C++ Test_f method signature
    }

    fun InitProcessor(module: String, forceGeneric: Boolean) {
        val cpuid = idLib.sys.GetProcessorId()
        val newProcessor: idSIMDProcessor = if (forceGeneric) {
            generic!!
        } else {
            // Processor selection logic would go here based on cpuid
            // For now, using generic as fallback
            generic!!
        }
        if (newProcessor != SIMDProcessor) {
            SIMDProcessor = newProcessor
            idLib.common.Printf("%s using %s for SIMD processing\n", module, SIMDProcessor!!.GetName())
        }

        // Set FPU settings for SSE support
        if ((cpuid and sys_public.CPUID_SSE) != 0) {
            idLib.sys.FPU_SetFTZ(true)
            idLib.sys.FPU_SetDAZ(true)
        }
    }

    fun Shutdown() {
        if (processor != generic) {
            // delete processor would go here in C++
            processor = null
        }
        // delete generic would go here in C++
        generic = null
        processor = null
        SIMDProcessor = null
    }

}

abstract class idSIMDProcessor {
    var cpuid: Int = sys_public.CPUID_NONE
    abstract fun  /*char *VPCALL*/GetName(): String
    abstract fun Add(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
    abstract fun Add(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
    abstract fun Sub(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
    abstract fun Sub(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
    abstract fun Mul(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
    abstract fun Mul(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
    abstract fun Div(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
    abstract fun Div(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
    abstract fun MulAdd(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
    abstract fun MulAdd(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
    abstract fun MulSub(dst: FloatArray, constant: Float, src: FloatArray, count: Int)
    abstract fun MulSub(dst: FloatArray, src0: FloatArray, src1: FloatArray, count: Int)
    abstract fun Dot(dst: FloatArray, constant: idVec3, src: Array<idVec3>, count: Int)
    abstract fun Dot(dst: FloatArray, constant: idVec3, src: Array<idPlane>, count: Int)
    abstract fun Dot(dst: FloatArray, constant: idVec3, src: Array<idDrawVert>, count: Int)
    abstract fun Dot(dst: FloatArray, constant: idPlane, src: Array<idVec3>, count: Int)
    abstract fun Dot(dst: FloatArray, constant: idPlane, src: Array<idPlane>, count: Int)
    abstract fun Dot(dst: FloatArray, constant: idPlane, src: Array<idDrawVert>, count: Int)
    abstract fun Dot(dst: FloatArray, src0: Array<idVec3>, src1: Array<idVec3>, count: Int)
    abstract fun Dot(dot: CFloat, src1: FloatArray, src2: FloatArray, count: Int)
    fun Dot(dot: CFloat, src1: FloatBuffer, src2: FloatArray, count: Int) {
        Dot(dot, TempDump.fbtofa(src1), src2, count)
    }

    abstract fun CmpGT(dst: ByteArray, src0: FloatArray, constant: Float, count: Int)
    abstract fun CmpGT(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)

    //abstract fun CmpGE(dst: ByteArray, src0: FloatArray, constant: Float, count: Int)
    abstract fun CmpGE(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)
    abstract fun CmpLT(dst: ByteArray, src0: FloatArray, constant: Float, count: Int)
    abstract fun CmpLT(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)
    abstract fun CmpLE(dst: ByteArray, src0: FloatArray, constant: Float, count: Int)
    abstract fun CmpLE(dst: ByteArray, bitNum: Byte, src0: FloatArray, constant: Float, count: Int)
    abstract fun MinMax(min: CFloat, max: CFloat, src: FloatArray, count: Int)
    abstract fun MinMax(min: idVec2, max: idVec2, src: Array<idVec2>, count: Int)
    abstract fun MinMax(min: idVec3, max: idVec3, src: Array<idVec3>, count: Int)
    abstract fun MinMax(min: idVec3, max: idVec3, src: Array<idDrawVert>, count: Int)
    abstract fun MinMax(
        min: idVec3,
        max: idVec3,
        src: Array<idDrawVert>,
        indexes: IntArray,
        count: Int
    )

    abstract fun Clamp(dst: FloatArray, src: FloatArray, min: Float, max: Float, count: Int)
    abstract fun ClampMin(dst: FloatArray, src: FloatArray, min: Float, count: Int)
    abstract fun ClampMax(dst: FloatArray, src: FloatArray, max: Float, count: Int)

    @Deprecated("")
    abstract fun Memcpy(dst: Array<Any>, src: Array<Any>, count: Int)
    fun Memcpy(dst: Array<idAnimBlend>, src: Array<idAnimBlend>, count: Int) {
        for (i in 0 until count) {
            dst[i] = idAnimBlend(src[i])
        }
    }

    fun Memcpy(dst: Array<idDrawVert>, src: Array<idDrawVert>, count: Int) {
        for (i in 0 until count) {
            dst[i] = idDrawVert(src[i])
        }
    }

    fun Memcpy(dst: Array<idJointQuat>, src: Array<idJointQuat>, count: Int) {
        for (i in 0 until count) {
            dst[i] = idJointQuat(src[i])
        }
    }

    fun Memcpy(dst: Array<shadowCache_s>, src: Array<idVec4>, count: Int) {
        for (i in 0 until count) {
            dst[i].xyz.set(src[i])
        }
    }

    fun Memcpy(dst: Array<shadowCache_s>, src: Array<shadowCache_s>, count: Int) {
        for (i in 0 until count) {
            dst[i].xyz.set(src[i].xyz)
        }
    }

    fun Memcpy(dst: Any, src: Any, count: Int) {
        // Generic memory copy for Any types
        // This would need specific implementation based on actual types
        // For now, this is a placeholder matching the C++ signature
    }

    @Deprecated("")
    abstract fun Memset(dst: Array<Any>, `val`: Int, count: Int)

    //	// these assume 16 byte aligned and 16 byte padded memory
    abstract fun Zero16(dst: FloatArray, count: Int)
    abstract fun Negate16(dst: FloatArray, count: Int)
    abstract fun Copy16(dst: FloatArray, src: FloatArray, count: Int)
    abstract fun Add16(dst: FloatArray, src1: FloatArray, src2: FloatArray, count: Int)
    abstract fun Sub16(dst: FloatArray, src1: FloatArray, src2: FloatArray, count: Int)
    abstract fun Mul16(dst: FloatArray, src1: FloatArray, constant: Float, count: Int)
    abstract fun AddAssign16(dst: FloatArray, src: FloatArray, count: Int)
    abstract fun SubAssign16(dst: FloatArray, src: FloatArray, count: Int)
    abstract fun MulAssign16(dst: FloatArray, constant: Float, count: Int)

    //	// idMatX operations
    abstract fun MatX_MultiplyVecX(dst: idVecX, mat: idMatX, vec: idVecX)
    abstract fun MatX_MultiplyAddVecX(dst: idVecX, mat: idMatX, vec: idVecX)
    abstract fun MatX_MultiplySubVecX(dst: idVecX, mat: idMatX, vec: idVecX)
    abstract fun MatX_TransposeMultiplyVecX(dst: idVecX, mat: idMatX, vec: idVecX)
    abstract fun MatX_TransposeMultiplyAddVecX(dst: idVecX, mat: idMatX, vec: idVecX)
    abstract fun MatX_TransposeMultiplySubVecX(dst: idVecX, mat: idMatX, vec: idVecX)
    abstract fun MatX_MultiplyMatX(dst: idMatX, m1: idMatX, m2: idMatX)
    abstract fun MatX_TransposeMultiplyMatX(dst: idMatX, m1: idMatX, m2: idMatX)
    abstract fun MatX_LowerTriangularSolve(
        L: idMatX,
        x: FloatArray,
        b: FloatArray,
        n: Int /*, int skip = 0*/
    )

    fun MatX_LowerTriangularSolve(L: idMatX, x: FloatArray, b: FloatBuffer, n: Int /*, int skip = 0*/) {
        MatX_LowerTriangularSolve(L, x, TempDump.fbtofa(b), n)
    }

    abstract fun MatX_LowerTriangularSolve(L: idMatX, x: FloatArray, b: FloatArray, n: Int, skip: Int)
    abstract fun MatX_LowerTriangularSolveTranspose(L: idMatX, x: FloatArray, b: FloatArray, n: Int)
    abstract fun MatX_LDLTFactor(mat: idMatX, invDiag: idVecX, n: Int): Boolean

    // rendering
    abstract fun BlendJoints(
        joints: Array<idJointQuat>,
        blendJoints: Array<idJointQuat>,
        lerp: Float,
        index: IntArray,
        numJoints: Int
    )

    abstract fun ConvertJointQuatsToJointMats(
        jointMats: Array<idJointMat>,
        jointQuats: Array<idJointQuat>,
        numJoints: Int
    )

    abstract fun ConvertJointMatsToJointQuats(
        jointQuats: List.idList<idJointQuat>,
        jointMats: Array<idJointMat>,
        numJoints: Int
    )

    abstract fun TransformJoints(
        jointMats: Array<idJointMat>,
        parents: IntArray,
        firstJoint: Int,
        lastJoint: Int
    )

    abstract fun UntransformJoints(
        jointMats: Array<idJointMat>,
        parents: IntArray,
        firstJoint: Int,
        lastJoint: Int
    )

    abstract fun TransformVerts(
        verts: Array<idDrawVert>,
        numVerts: Int,
        joints: Array<idJointMat>,
        weights: Array<idVec4>,
        index: IntArray,
        numWeights: Int
    )

    abstract fun TracePointCull(
        cullBits: ByteArray,
        totalOr: ByteArray,
        radius: Float,
        planes: Array<idPlane>,
        verts: Array<idDrawVert>,
        numVerts: Int
    )

    abstract fun DecalPointCull(
        cullBits: ByteArray,
        planes: Array<idPlane>,
        verts: Array<idDrawVert>,
        numVerts: Int
    )

    abstract fun OverlayPointCull(
        cullBits: ByteArray,
        texCoords: Array<idVec2>,
        planes: Array<idPlane>,
        verts: Array<idDrawVert>,
        numVerts: Int
    )

    abstract fun DeriveTriPlanes(
        planes: Array<idPlane>,
        verts: Array<idDrawVert>,
        numVerts: Int,
        indexes: IntArray,
        numIndexes: Int
    )

    abstract fun DeriveTangents(
        planes: Array<idPlane>,
        verts: Array<idDrawVert>,
        numVerts: Int,
        indexes: IntArray,
        numIndexes: Int
    )

    abstract fun DeriveUnsmoothedTangents(
        verts: Array<idDrawVert>,
        dominantTris: Array<dominantTri_s>,
        numVerts: Int
    )

    abstract fun NormalizeTangents(verts: Array<idDrawVert>, numVerts: Int)
    abstract fun CreateTextureSpaceLightVectors(
        lightVectors: Array<idVec3>,
        lightOrigin: idVec3,
        verts: Array<idDrawVert>,
        numVerts: Int,
        indexes: IntArray,
        numIndexes: Int
    )

    fun CreateTextureSpaceLightVectors(
        localLightVector: idVec3,
        localLightOrigin: idVec3,
        verts: Array<idDrawVert>,
        numVerts: Int,
        indexes: IntArray,
        numIndexes: Int
    ) {
        // This overload should calculate texture space light vectors
        // Implementation would match the C++ version
        // For now, this is a placeholder
    }

    abstract fun CreateSpecularTextureCoords(
        texCoords: Array<idVec4>,
        lightOrigin: idVec3,
        viewOrigin: idVec3,
        verts: Array<idDrawVert>,
        numVerts: Int,
        indexes: IntArray,
        numIndexes: Int
    )

    abstract fun CreateShadowCache(
        vertexCache: Array<idVec4>,
        vertRemap: IntArray,
        lightOrigin: idVec3,
        verts: Array<idDrawVert>,
        numVerts: Int
    ): Int

    abstract fun CreateVertexProgramShadowCache(
        vertexCache: Array<idVec4>,
        verts: Array<idDrawVert>,
        numVerts: Int
    ): Int

    fun CreateVertexProgramShadowCache(vertexCache: idVec4, verts: Array<idDrawVert>, numVerts: Int) {
        // This overload should create vertex program shadow cache
        // Implementation would match the C++ version
        // For now, this is a placeholder
    }

    // sound mixing
    abstract fun UpSamplePCMTo44kHz(
        dest: FloatArray,
        pcm: ShortArray,
        numSamples: Int,
        kHz: Int,
        numChannels: Int
    )

    fun UpSampleOGGTo44kHz(
        dest: FloatArray,
        ogg: Array<FloatArray>,
        numSamples: Int,
        kHz: Int,
        numChannels: Int
    ) {
        this.UpSampleOGGTo44kHz(dest, 0, ogg, numSamples, kHz, numChannels)
    }

    abstract fun UpSampleOGGTo44kHz(
        dest: FloatArray,
        offset: Int,
        ogg: Array<FloatArray>,
        numSamples: Int,
        kHz: Int,
        numChannels: Int
    )

    abstract fun UpSampleOGGTo44kHz(
        dest: FloatBuffer,
        offset: Int,
        ogg: Array<FloatArray>,
        numSamples: Int,
        kHz: Int,
        numChannels: Int
    )

    abstract fun MixSoundTwoSpeakerMono(
        mixBuffer: FloatArray,
        samples: FloatArray,
        numSamples: Int,
        lastV: FloatArray,
        currentV: FloatArray
    )

    abstract fun MixSoundTwoSpeakerStereo(
        mixBuffer: FloatArray,
        samples: FloatArray,
        numSamples: Int,
        lastV: FloatArray,
        currentV: FloatArray
    )

    abstract fun MixSoundSixSpeakerMono(
        mixBuffer: FloatArray,
        samples: FloatArray,
        numSamples: Int,
        lastV: FloatArray,
        currentV: FloatArray
    )

    abstract fun MixSoundSixSpeakerStereo(
        mixBuffer: FloatArray,
        samples: FloatArray,
        numSamples: Int,
        lastV: FloatArray,
        currentV: FloatArray
    )

    abstract fun MixedSoundToSamples(
        samples: ShortArray,
        offset: Int,
        mixBuffer: FloatArray,
        numSamples: Int
    )

    fun MixedSoundToSamples(samples: ShortArray, mixBuffer: FloatArray, numSamples: Int) {
        MixedSoundToSamples(samples, 0, mixBuffer, numSamples)
    }

    fun Memset(cullBits: ByteArray, i: Int, numVerts: Int) {
        Arrays.fill(cullBits, 0, numVerts, i.toByte())
    }

    fun Memset(cullBits: IntArray, i: Int, numVerts: Int) {
        Arrays.fill(cullBits, 0, numVerts, i)
    }

    /*
    ============
    idSIMD_Generic::CmpGE

      dst[i] = src0[i] >= constant;
    ============
    */
    open fun CmpGE(facing: ByteArray, planeSide: FloatArray, f: Float, numFaces: Int) {
        val nm = numFaces and -0x4
        var i: Int = 0
        while (i < nm) {
            facing[i + 0] = TempDump.btoi(planeSide[i + 0] >= f).toByte()
            facing[i + 1] = TempDump.btoi(planeSide[i + 1] >= f).toByte()
            facing[i + 2] = TempDump.btoi(planeSide[i + 2] >= f).toByte()
            facing[i + 3] = TempDump.btoi(planeSide[i + 3] >= f).toByte()
            i += 4
        }
        while (i < numFaces) {
            facing[i + 0] = TempDump.btoi(planeSide[i + 0] >= f).toByte()
            i++
        }
    }

    fun Memcpy(dst: IntArray, src: IntArray, count: Int) {
        Memcpy(dst, 0, src, 0, count)
    }

    fun Memcpy(dst: IntArray, dstOffset: Int, src: IntArray, srcOffset: Int, count: Int) {
        System.arraycopy(src, srcOffset, dst, dstOffset, count)
    }

}