package nallar.tickprofiler.util.contextaccess;

public interface ContextAccess {
	ContextAccess $ = ContextAccessProvider.getContextAccess();

	Class getContext(int depth);
}
