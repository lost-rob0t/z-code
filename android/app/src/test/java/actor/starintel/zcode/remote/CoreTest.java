package actor.starintel.zcode.remote;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public final class CoreTest {
    @Test public void policyStateAndCrypto() throws Exception { assertTrue(CoreSpec.run() > 500); }
}
