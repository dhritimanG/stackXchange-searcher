import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        // write your code here
        List<String> tags = new ArrayList<String>(Arrays.asList("list","python","comprehension"));
        AccessStackExchange accessStackExchange = new AccessStackExchange();
        accessStackExchange.getQuestions(tags);
        accessStackExchange.getUserProfile();
    }
}
