package neo.Renderer

import org.lwjgl.opengl.EXTTextureCompressionS3TC

/*
 PROBLEM: compressed textures may break the zero clamp rule!
 */
fun FormatIsDXT(internalFormat: Int): Boolean {
    return (internalFormat >= EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT && internalFormat <= EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT)
}

fun MakePowerOfTwo(num: Int): Int {
    var pot: Int
    pot = 1
    while (pot < num) {
        pot = pot shl 1
    }
    return pot
}
