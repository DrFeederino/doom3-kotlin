package neo.framework

@Deprecated("")
class Unzip {
    internal class unzFile

    /* tm_unz contain date/time info */
    internal class tm_unz {
        var tm_hour /* hours since midnight - [0,23] */ = 0
        var tm_mday /* day of the month - [1,31] */ = 0
        var tm_min /* minutes after the hour - [0,59] */ = 0
        var tm_mon /* months since January - [0,11] */ = 0
        var tm_sec /* seconds after the minute - [0,59] */ = 0
        var tm_year /* years - [1980..2044] */ = 0
    } /*tm_unz_s*/

    /* unz_global_info structure contain global data about the ZIPfile
     These data comes from the end of central dir */
    internal class unz_global_info {
        var number_entry /* total number of entries in the central dir on this disk */: Long = 0
        var size_comment /* size of the global comment of the zipfile */: Long = 0
    } /*unz_global_info_s*/

    /* unz_file_info contain information about a file in the zipfile */
    internal class unz_file_info {
        var compressed_size /* compressed size                 4 unsigned chars */: Long = 0
        var compression_method /* compression method              2 unsigned chars */: Long = 0
        var crc /* crc-32                          4 unsigned chars */: Long = 0
        var disk_num_start /* disk number start               2 unsigned chars */: Long = 0
        var dosDate /* last mod file date in Dos fmt   4 unsigned chars */: Long = 0
        var external_fa /* external file attributes        4 unsigned chars */: Long = 0
        var flag /* general purpose bit flag        2 unsigned chars */: Long = 0
        var internal_fa /* internal file attributes        2 unsigned chars */: Long = 0
        var size_file_comment /* file comment length             2 unsigned chars */: Long = 0
        var size_file_extra /* extra field length              2 unsigned chars */: Long = 0
        var size_filename /* filename length                 2 unsigned chars */: Long = 0

        //
        var tmu_date: tm_unz? = null
        var uncompressed_size /* uncompressed size               4 unsigned chars */: Long = 0
        var version /* version made by                 2 unsigned chars */: Long = 0
        var version_needed /* version needed to extract       2 unsigned chars */: Long = 0
    } /*unz_file_info_s*/

    internal class unz_file_info_internal {
        var offset_curfile /* relative offset of static header 4 unsigned chars */: Long = 0
    } /*unz_file_info_internal_s*/
}