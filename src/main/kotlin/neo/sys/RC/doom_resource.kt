package neo.sys.RC

import java.awt.Image
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import javax.imageio.ImageIO

object doom_resource {
    const val IDB_BITMAP_LOGO = 4000
    var IDI_ICON1: Image? = null

    init {
        try {
            IDI_ICON1 =
                ImageIO.read(doom_resource::class.java.getResource("res/doom.bmp")) //TODO: use a transparent png instead yo!
        } catch (ex: IOException) {
            Logger.getLogger(doom_resource::class.java.name).log(Level.SEVERE, null, ex) //TODO: log to doom console.
        }
    }
}