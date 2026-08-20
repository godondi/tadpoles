import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.neueda.leap.Main;

public class MainTest {
    @Test 
    public void testJacoco() {
        assertEquals("Hello world", Main.HelloWorld(null));
    }
}