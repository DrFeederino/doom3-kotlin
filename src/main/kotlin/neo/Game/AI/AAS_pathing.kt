package neo.Game.AI

const val SUBSAMPLE_FLY_PATH = 0
const val SUBSAMPLE_WALK_PATH = 1
const val flyPathSampleDistance = 8.0f
const val maxFlyPathDistance = 500.0f
const val maxFlyPathIterations = 10
const val maxWalkPathDistance = 500.0f
const val maxWalkPathIterations = 10
const val walkPathSampleDistance = 8.0f

class wallEdge_s {
    var edgeNum = 0
    var next: wallEdge_s? = null
    var verts: IntArray = IntArray(2)
}