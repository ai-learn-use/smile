package smile.clustering

import scala.collection.mutable
import scala.util.Random
import smile.math.distance.{Distance, Hamming}
/**
 * @author Beck Gaël
 * K-Modes scala implementation. K-Modes is the binary equivalent for K-Means. The mean update for centroids is replace by the mode one which is a majority vote among element of each cluster. This algorithm is Hamming oriented because the computation of the mode is the majority vote exclusively for Hamming distance, for other distance we have to compute the similarity matrix which is in O(n<sup>2</sup>).
 * Complexity is O(k.t.n) with Hamming distance, quadratic with other metrics. 
 * Link towards linked articles or other implementation :
 * - http://www.irma-international.org/viewtitle/10828/
 * - https://www.ijecs.in/index.php/ijecs/article/download/3058/2834/
 * - https://pypi.python.org/pypi/kmodes/
 * @param data : the dataset with id
 * @param k : number of clusters
 * @param epsilon : distance between ancient modes and update modes under which we consider than the algorithm have converged, if and only if all every mode have converged
 * @param maxIter : number maximal of iteration
 * @param metric : the dissimilarity measure used
 **/
class KModes(data: Seq[(Int, Array[Int])], var k: Int, var epsilon: Double, var maxIter: Int, var metric: Distance[Array[Int]]) {

	type ClusterID = Int
	type BinaryVector = Array[Int]

	val dim = data.head._2.size

	def sumTwoBinaryVector(vector1: BinaryVector, vector2: BinaryVector) = for( i <- vector1.indices.toArray ) yield( vector1(i) + vector2(i) )

	def apply(): KModesModel = {
		// Random initialization of modes and set their cardinalities to 0
		val kmodes = mutable.HashMap((for( clusterID <- 0 until k ) yield( (clusterID, Array.fill(dim)(Random.nextInt(2))) )):_*)
		val clusterCentroids = kmodes.map{ case (clusterID, _) => (clusterID, 0) }
		/**
		 * Return the nearest mode for a specific point
		 **/
		def obtainNearestModID(v: Array[Int]): ClusterID = kmodes.toArray.map{ case(clusterID, mod) => (clusterID, metric.d(mod, v)) }.sortBy(_._2).head._1

		/**
		 * Compute the similarity matrix and extract point which is the closest from all other point according to its dissimilarity measure
		 **/
		def obtainMode(arr: Seq[Array[Int]]): Array[Int] = {
			(for( v1 <- arr) yield( (v1, (for( v2 <- arr ) yield(metric.d(v1, v2))).sum / arr.size) )).sortBy(_._2).head._1
		}

		val zeroMode = Array.fill(dim)(0)
		var cpt = 0
		var allModsHaveConverged = false
		while( cpt < maxIter && ! allModsHaveConverged )
		{
			// Allocation to modes
			val clusterized = data.map{ case (id, v) => (id, v, obtainNearestModID(v)) }
			// Cloning modes to later comparaison
			val kModesBeforeUpdate = kmodes.clone
			// Reinitialization of modes
			kmodes.foreach{ case (clusterID, _) => kmodes(clusterID) = zeroMode }
			clusterCentroids.foreach{ case (clusterID, _) => clusterCentroids(clusterID) = 0 }
			
			// Fast way if we use Hamming distance
			if( metric.isInstanceOf[Hamming] ) {
				// Updatating Modes
				clusterized.foreach{ case (_, v, clusterID) =>
				{
					kmodes(clusterID) = sumTwoBinaryVector(kmodes(clusterID), v)
					clusterCentroids(clusterID) += 1
				}}
				// Updating modes
				kmodes.foreach{ case (clusterID, mod) => kmodes(clusterID) = mod.map( v => if( v * 2 >= clusterCentroids(clusterID) ) 1 else 0 ) }
			}
			// Define a mode by searching it through computation of the similarity matrix
			else {
				clusterized.groupBy{ case (_, _, clusterID) => clusterID }.foreach{ case (clusterID, aggregates) =>
				{
					val cluster = aggregates.map{ case (_, vector, _) => vector }
					val mode = obtainMode(cluster)
					kmodes(clusterID) = mode
				}}
			}

			// Check if every mode have converged
			allModsHaveConverged = kModesBeforeUpdate.forall{ case (clusterID, previousMod) => metric.d(previousMod, kmodes(clusterID)) <= epsilon }

			cpt += 1
		}

		new KModesModel(kmodes, clusterCentroids, metric)
	}
}

object KModes {

	type ID = Int
	type BinaryVector = Array[Int]	

	def run(data: Seq[(ID, BinaryVector)], k: Int, epsilon: Double, maxIter: Int, metric: Distance[Array[Int]]): KModesModel = {
		val kmodes = new KModes(data, k, epsilon, maxIter, metric)
		val kModesModel = kmodes.apply()
		kModesModel
	}
}