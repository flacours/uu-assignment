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
    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao) {
        userDao = udao;
        itemDao = idao;
    }

    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        SparseVector userVector = getUserRatingVector(user);

        List<Neighbor> neighbors = getBestNeighbor(user, userVector, NEIGHBOR_SIZE);
        printList(neighbors);
        // TODO Score items for this user using user-user collaborative filtering

        // This is the loop structure to iterate over items to score
        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {

        }
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
     * @param user The user ID.
     * @param userRatings The user rating vector.
     * @param listSize  size of the returns list.
     * @return 
     */
    private List<Neighbor> getBestNeighbor(long user, SparseVector userVector, int listSize)
    {
    	List<Neighbor> neighborList = new ArrayList<Neighbor>();
    	CosineVectorSimilarity cosineVector = new CosineVectorSimilarity();
    	for(UserHistory<Event> u : userDao.streamEventsByUser().fast())
    	{
    		long neighborId = u.getUserId();
    		if(neighborId == user) continue;
    		SparseVector neighborVector = getUserRatingVector(neighborId);
    		double similarity = cosineVector.similarity(userVector, neighborVector);
    		neighborList.add(new Neighbor(neighborId, neighborVector, similarity));
    	}
    	Collections.sort(neighborList, new NeighborComparator());
    	return neighborList.subList(0, listSize);
    }

	private void printList(List<Neighbor> neighborList) {
		try
    	{
    	for(Neighbor n : neighborList) {
    		System.out.printf("%d %f %f\n", n.getUserId(), n.getSimilarity(), n.getMean());
    	}
    	}
    	catch(Exception ex)
    	{
    		System.out.println("Exception : " + ex.getMessage());
    	}
	}
    
    private class Neighbor
    {
    	private long userId;
    	private SparseVector userRating;
    	private double similarity;
    	public Neighbor(long userId, SparseVector userRating, double similarity)
    	{
    		setUserId(userId);
    		setUserRating(userRating);
    		setSimilarity(similarity);
    	}
		public long getUserId() {
			return userId;
		}
		public void setUserId(long userId) {
			this.userId = userId;
		}
		public SparseVector getUserRating() {
			return userRating;
		}
		public void setUserRating(SparseVector userRating) {
			this.userRating = userRating;
		}
		public double getSimilarity() {
			return similarity;
		}
		public void setSimilarity(double similarity) {
			this.similarity = similarity;
		}
		public double getMean()
		{
			return userRating.mean();
		}
    }
    
    private class NeighborComparator implements Comparator<Neighbor> {
    	@Override
    	public int compare(Neighbor o1, Neighbor o2) {
    		if(o1.getSimilarity() < o2.getSimilarity()) return 1;
    		else if(o1.getSimilarity() > o2.getSimilarity()) return -1; 
    		return 0;
    	}
    }    

    
}


