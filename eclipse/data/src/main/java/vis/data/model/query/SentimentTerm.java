package vis.data.model.query;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.tuple.Pair;

import vis.data.model.RawSentiment;
import vis.data.model.meta.DocForLemmaAccessor;
import vis.data.model.meta.SentimentAccessor;
import vis.data.util.CountAggregator;

public class SentimentTerm extends Term {
	public static class Parameters implements Term.Parameters {
		public boolean filterOnly_;
		public String category_;

		@Override
		public int hashCode() {
			int hashCode = new Boolean(filterOnly_).hashCode();
			hashCode ^= Parameters.class.hashCode();
			if(category_ != null)
				hashCode ^= category_.hashCode();
			return hashCode;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(!Parameters.class.isInstance(obj))
				return false;
			Parameters p = (Parameters)obj;
			if(filterOnly_ != p.filterOnly_) {
				return false;
			}
			if(category_ != null ^ p.category_ != null) {
				return false;
			}
			return true;
		}	
		@Override
		public ResultType resultType() {
			return ResultType.DOC_HITS;
		}

		@Override
		public void validate() {
			if(category_ == null)
				throw new RuntimeException("must specify either a sentiment name");
		}
		@Override
		public Collection<Term.Parameters> withChildren() {
			return Arrays.asList((Term.Parameters)this);
		}
		@Override
		public void setFilterOnly() {
			filterOnly_ = true;
		}
	}
	
	public final Parameters parameters_;
	public final RawSentiment sentiment_; 
	public final int docs_[];
	public final int count_[];
	public SentimentTerm(Parameters parameters) throws SQLException {
		int lemmas[];
		DocForLemmaAccessor dlh = new DocForLemmaAccessor();
		SentimentAccessor sa = new SentimentAccessor();
		parameters_ = parameters;

		sentiment_ = sa.lookupSentiment(parameters.category_);
		lemmas = sa.getLemmasForSentiment(sentiment_.id_);
		
		if(lemmas.length == 0) {
			docs_ = new int[0];
			count_ = new int[0];
		} else {
			DocForLemmaAccessor.Counts initial = dlh.getDocCounts(lemmas[0]);
			for(int i = 1; i < lemmas.length; ++i) {
				DocForLemmaAccessor.Counts partial = dlh.getDocCounts(lemmas[1]);
				Pair<int[], int[]> res = CountAggregator.or(initial.docId_, initial.count_, partial.docId_, partial.count_);
				initial.docId_ = res.getKey();
				initial.count_ = res.getValue();
			}
			docs_ = initial.docId_;
			//TODO: wasteful
			count_ = parameters_.filterOnly_ ? new int[docs_.length] : initial.count_;
		}
	}

	public Term.Parameters parameters() {
		return parameters_;
	}	

	@Override
	public Pair<int[], int[]> compute() throws SQLException {
		if(parameters_.filterOnly_)
			return Pair.of(docs_, new int[docs_.length]);
		return Pair.of(docs_, count_);
	}
}
