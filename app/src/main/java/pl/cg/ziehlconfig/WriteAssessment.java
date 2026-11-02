package pl.cg.ziehlconfig;

/** Classify post-write readbacks. Missing FC06 echo alone cannot prove failure. */
public final class WriteAssessment {
    public static final int UNKNOWN=0;
    public static final int STORED_OLD_LINK=1;
    public static final int STORED_NEW_LINK=2;
    public static final int STILL_OLD=3;
    private WriteAssessment() {}
    public static int classify(int oldValue,int targetValue,int oldRead,int newRead) {
        if(oldRead==targetValue) return STORED_OLD_LINK;
        if(newRead==targetValue) return STORED_NEW_LINK;
        if(oldRead==oldValue && oldValue!=targetValue) return STILL_OLD;
        return UNKNOWN;
    }
}
