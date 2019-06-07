import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.core.Response;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.StringUtils;

/**
 * Hits the StackExchange API to allows users to collect data and questions related to various tags
 */
public class AccessStackExchange {
  String baseEndpoint = "https://api.stackexchange.com/2.2/";
  int pagesize = 100;
  String baseQueryParam = "?site=stackoverflow&key=dQ7bPWsLoUCK8)ay8lAC8g((";
  ResteasyClient client = new ResteasyClientBuilder().build();
  int goldBadgeScore = 3;
  int silverBadgeScore = 2;
  int bronzeBadgeScore = 1;
  Map<Integer, List<String>> questions = new HashMap<Integer, List<String>>();

  /**
   * Queries Questions API and gets response from StackExchange for multiple tags passed as
   * parameter
   */
  public void getQuestions(List<String> tags) throws IOException {
    for (String tag : tags) {
      String fullEndpoint = baseEndpoint + "questions" + baseQueryParam + "&tagged=" + tag + "&pagesize=" + pagesize;
      ResteasyWebTarget target = client.target(fullEndpoint);
      Response response = target.request().get();
      String value = response.readEntity(String.class);
      JSONObject json = new JSONObject(value);
      if (json.has("items")) {
        storeUserIdAndQuestions(json);
      }
      response.close();
    }
  }


  /**
   * Parses the JSON from the response and stores user_id and their corresponding list of questions
   * in a Hashmap
   */
  private void storeUserIdAndQuestions(JSONObject json) throws IOException {
    JSONArray items = json.getJSONArray("items");
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.getJSONObject(i);
      JSONObject userInfo = item.getJSONObject("owner");
      if (userInfo.has("user_id") && item.has("title")) {
        Integer uid = userInfo.getInt("user_id");
        String q = item.getString("title");

        // If user_id is already present in Map, then update entry and
        // add to list of questions, else, create a new entry
        if (!questions.containsKey(uid)) {
          questions.put(uid, new ArrayList<String>(Arrays.asList(q)));
        } else {
          List<String> oldQues = questions.get(uid);
          oldQues.add(q);
          questions.put(uid, oldQues);
        }
      }
    }
  }

  /**
   * Queries Users API stores user_id and their badge_counts information in a HashMap
   */
  public void getUserProfile() throws IOException {
    Set<Integer> userIds = questions.keySet();

    // Passing users in batches of 100 as users-by-id api only allows fetching 100 users at at time and
    // persisting in badges Map
    List<Integer> userSubList = null;
    for (int j = 0; j < userIds.size(); j += 100) {
      if (j + 100 < userIds.size()) {
        userSubList = new ArrayList<Integer>(userIds).subList(j, j + 100);
      } else {
        userSubList = new ArrayList<Integer>(userIds).subList(j, j + userIds.size() % 100);
      }
      String fullEndpoint = baseEndpoint + "users/" + StringUtils.join(userSubList, ';') + baseQueryParam + "&pagesize=" + pagesize;
      Map<Integer, JSONObject> badges = new HashMap<Integer, JSONObject>();
      ResteasyWebTarget target = client.target(fullEndpoint);
      Response response = target.request().get();
      String value = response.readEntity(String.class);
      JSONObject json = new JSONObject(value);
      JSONArray userProfiles = json.getJSONArray("items");
      for (int i = 0; i < userProfiles.length(); i++) {
        JSONObject profile = userProfiles.getJSONObject(i);
        badges.put(profile.getInt("user_id"), profile.getJSONObject("badge_counts"));
      }
      calculateUserWeightageAndSortUsers(badges);
    }
  }


  /**
   * Calculates weightage of each user on the basis of badges earned.
   */
  private void calculateUserWeightageAndSortUsers(Map<Integer, JSONObject> badges) throws IOException {
    Map<Integer, Integer> weightage = new HashMap<Integer, Integer>();
    Iterator<Map.Entry<Integer, JSONObject>> entries = badges.entrySet().iterator();
    while (entries.hasNext()) {
      int score = 0;
      Map.Entry<Integer, JSONObject> entry = entries.next();
      Integer uid = entry.getKey();
      JSONObject scoreJson = entry.getValue();
      int scoreJsonLen = scoreJson.length();
      for (int i = 0; i < scoreJsonLen; i++) {
        score += (goldBadgeScore * scoreJson.getInt("gold")) +
                (silverBadgeScore * scoreJson.getInt("silver")) +
                (bronzeBadgeScore * scoreJson.getInt("bronze"));
      }
      weightage.put(uid, score);
    }
    writeTopQuestionsToFile(weightage);
  }


  /**
   * Writes to file questions of top users in descending order using the user-questions map
   */
  private void writeTopQuestionsToFile(Map<Integer, Integer> weightage) throws IOException {
    List<Map.Entry<Integer, Integer>> sortedUsers = sortUsersBasedOnWeightage(weightage);

    FileWriter writer = new FileWriter("output.txt");

    List<String> topQuestions;
    for (Map.Entry<Integer, Integer> user : sortedUsers) {
      Integer user_id = user.getKey();
      topQuestions = questions.get(user_id);
      writer.write("Questions for user with id " + user_id + " and weightage " + user.getValue() + " \n\n");
      for (int i = 0; i < topQuestions.size(); i++) {
        writer.write("\t\t " + (i + 1) + ". " + topQuestions.get(i) + "\n" + "\n\n");
      }
      writer.write("\n");
    }
    writer.close();
  }

  /**
   * Sorts users based on their weightage in descending order
   */
  private List<Map.Entry<Integer, Integer>> sortUsersBasedOnWeightage(Map<Integer, Integer> weightage) {
    final List<Map.Entry<Integer, Integer>> sortedUsers = new LinkedList<Map.Entry<Integer, Integer>>(weightage.entrySet());
    Collections.sort(sortedUsers, new Comparator<Map.Entry<Integer, Integer>>() {
      public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
        return o1.getValue().compareTo(o2.getValue());
      }
    });
    Collections.reverse(sortedUsers);
    return sortedUsers;
  }

}
