package neo.framework

object DemoChecksum {
    /*
     ===============================================================================

     Pak file checksum for demo build.

     ===============================================================================
     */
    // every time a new demo pk4 file is built, this checksum must be updated.
    // the easiest way to get it is to just run the game and see what it spits out
    const val DEMO_PAK_CHECKSUM: Long = -0x55873742
}