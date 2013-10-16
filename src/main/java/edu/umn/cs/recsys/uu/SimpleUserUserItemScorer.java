package edu.umn.cs.recsys.uu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final UserEventDAO userDao;
    private final ItemEventDAO itemDao;
    private static final int NEIGHBOR_SIZE = 30;
    private static final boolean _debug1 = false;
    private static final boolean _debug2 = false;
    // args 1024:77 1024:268 1024:462 1024:393 1024:36955 2048:77 2048:36955 2048:788
    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao) {
        userDao = udao;
        itemDao = idao;
    }

    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        SparseVector userVector = getUserRatingVector(user);

        
        // TODO Score items for this user using user-user collaborative filtering
        double meanUser = userVector.mean();
        
        // This is the loop structure to iterate over items to score
        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {
        	// equation score(i) = meanUser + (sum (similarity(u,v) * (r(v,i)-mean(v)) / sum( absolute(sim (u,v))
        	long itemId = e.getKey();
        	List<Neighbor> neighbors = getBestNeighbor(itemId, user, userVector, NEIGHBOR_SIZE);
            if(_debug1) printList(neighbors);
        	double num = computeNumerator(neighbors,itemId );
        	double denum = computeDenominator(neighbors);
        	double score = meanUser + num / denum;
        	if(_debug2) System.out.printf("%d score=%f (%f + %f/%f)\n", itemId, score, meanUser, num, denum);
        	scores.set(e.getKey(), score);
        }
    }
    
    /**
     * Compute numerator of scoring function
     * @param neighbors
     * @param itemId
     * @return (sum (similarity(u,v) * (r(v,i)-mean(v))
     */
    private double computeNumerator(List<Neighbor> neighbors, long itemId )
    {
    	double retVal = 0;
    	for(Neighbor n : neighbors)
    	{
    		double rating_v_i = n.userRating.get(itemId, 0);

    		double v = n.similarity * (rating_v_i - n.getMean());
    		retVal += v;
    	}
    	return retVal;
    }
    
    /**
     * Compute denominator of scoring function
     * @param neighbors
     * @return sum( absolute(neibors.similarities  )
     */
    private double computeDenominator(List<Neighbor> neighbors)
    {
    	double retVal = 0;
    	for(Neighbor n : neighbors)
    	{
    		retVal += Math.abs(n.similarity);
    	}
    	return retVal;
    }
    

    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userDao.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }
    
   
    
    /**
     * Get a user best neighbor
     * @param userId The user ID.
     * @param userRatings The user rating vector.
     * @param listSize  size of the returns list.
     * @return 
     */
    private List<Neighbor> getBestNeighbor(long itemId, long userId, SparseVector userVector, int listSize)
    {
    	List<Neighbor> neighborList = new ArrayList<Neighbor>();
    	CosineVectorSimilarity cosineVector = new CosineVectorSimilarity();
    	MutableSparseVector msUser = userVector.mutableCopy();
    	msUser.add(-1*msUser.mean());
    	for(UserHistory<Event> u : userDao.streamEventsByUser().fast())
    	{
    		long neighborId = u.getUserId();
    		if(neighborId == userId) continue;
    		SparseVector neighborVector = getUserRatingVector(neighborId);
    		if(neighborVector.containsKey(itemId))
    		{
    			MutableSparseVector ms = neighborVector.mutableCopy();
    			ms.add(-1*ms.mean());
    			double similarity = cosineVector.similarity(msUser, ms);
    			neighborList.add(new Neighbor(neighborId, ms.freeze(), similarity));
    		}
    	}
    	Collections.sort(neighborList, new NeighborComparator());
    	return neighborList.subList(0, listSize);
    }

    private void printList(List<Neighbor> neighborList) 
    {
    	for(Neighbor n : neighborList) {
    		System.out.printf("%d %f %f\n", n.userId, n.similarity, n.getMean());
    	}
    }
    
    private class Neighbor
    {
    	public final long userId;
    	public final SparseVector userRating;
    	public final double similarity;
    	public Neighbor(long userId, SparseVector userRating, double similarity)
    	{
    		this.userId=userId;
    		this.userRating=userRating;
    		this.similarity=similarity;
    	}
		public double getMean()
		{
			return userRating.mean();
		}
    }
    
    private class NeighborComparator implements Comparator<Neighbor> {
    	@Override
    	public int compare(Neighbor o1, Neighbor o2) {
    		if(o1.similarity < o2.similarity) return 1;
    		else if(o1.similarity > o2.similarity) return -1; 
    		return 0;
    	}
    }    

    
}


