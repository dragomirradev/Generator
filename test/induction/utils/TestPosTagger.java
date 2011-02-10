package induction.utils;

import junit.framework.TestCase;

/**
 *
 * @author konstas
 */
public class TestPosTagger extends TestCase
{
    PosTagger posTagger;

    public TestPosTagger(String name)
    {
        super(name);
        posTagger = new PosTagger();
    }

    public void testPosTagger()
    {        
        assertEquals("./.", posTagger.tag(".").trim());
        assertEquals(",/,", posTagger.tag(",").trim());
        assertEquals("-LRB-/-LRB-", posTagger.tag("(").trim());
        assertEquals("-RRB-/-RRB-", posTagger.tag(")").trim());
        assertEquals("--/:", posTagger.tag("--").trim());
        assertEquals("and/CC", posTagger.tag("and").trim());
    }
}