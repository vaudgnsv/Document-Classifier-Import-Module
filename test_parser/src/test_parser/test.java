package test_parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class test {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		String module_id = args[0], taxonomy_path = args[1], concept_path = args[2];

		File taxonomy_file = new File(taxonomy_path); // taxonomy 파일
		String line = null;

		JSONArray taxonomy = null;
		StringBuilder sb = new StringBuilder();
		BufferedReader bufReader = new BufferedReader( // UTF-8 포맷으로 읽어들임
				new InputStreamReader(new FileInputStream(taxonomy_file), "UTF-8"));
		while ((line = bufReader.readLine()) != null) {
			sb.append(line);
		}

		//JSONParser jsonParser = new JSONParser(); // JSON 파서
		System.out.println(sb.toString());
		JSONTokener tokener = new JSONTokener(sb.toString()); // Tokener로 파싱
		taxonomy = new JSONArray(tokener);
		json_arr_parse(module_id, taxonomy); // JSONArray 데이터 추출
		insert_component(module_id, concept_path); // component table 삽입
		insert_class(module_id, taxonomy); // class table 삽입

	}

	/* query로 들어온 xml을 rule table에 넣기 위해 JSONArray로 변환 */
	public static JSONArray getJSON(String query) throws Exception {
		Document xmlDoc = loadXMLFromString(query); // query로 들어온 xml을 Dom 형태로 파싱
		Element root = xmlDoc.getDocumentElement();
		Node node = root.cloneNode(true); // root node
		if (node == null)
			System.out.println("root node null");

		JSONObject name = new JSONObject();
		JSONObject Rule_row = new JSONObject();
		name.put("type", node.getNodeName());
		if (node.getNodeName().contains("ConceptQuery")) // root node의 name이 ConceptQuery인 경우
			Rule_row.put("RULE_TY", "ConceptQuery"); // root node의 rule type를 ConceptQuery로 지정
		else
			Rule_row.put("RULE_TY", "operator"); // ConceptQuery가 아니면 type를 operator로 지정
		if (node.getAttributes().getLength() == 0) // Attribute가 없는 경우
			Rule_row.put("RULE_NM", node.getNodeName());
		else { // Attribute가 있는 경우
			if (node.getNodeName().equals("BooleanQuery")) // BooleanQuery의 RULE_NM
				for (int i = 0; i < node.getAttributes().getLength(); i++) {
					Rule_row.put("RULE_NM", node.getNodeName() + "(" + node.getAttributes().item(i).getNodeName() + "=" // =넣기
							+ node.getAttributes().item(i).getNodeValue() + ")");
				}
			else {
				for (int i = 0; i < node.getAttributes().getLength(); i++) // BooleanQuery가 없는 경우
					Rule_row.put("RULE_NM",
							node.getNodeName() + "(" + node.getAttributes().item(i).getNodeValue() + ")");
			}
		}
		Rule_row.put("RULE_ATTR", name); // RULE_ATTR Column 데이터
		String root_id = UUID.randomUUID().toString();
		Rule_row.put("RULE_ID", root_id); // RULE_ID Column 데이터
		Rule_row.put("RULE_PARENT", "#"); // 처음 root node의 RULE_PATENT 데이터
		JSONArray Rule = new JSONArray();
		Rule.put(Rule_row); // JSONArray에 여태 정리한 JSONObject를 넣는다.
		node = root.getFirstChild(); // 첫번째 자식으로 탐색

		recursive(node, Rule, root_id); // 재귀적 탐색
		System.out.println();
		for (int i = 0; i < Rule.length(); i++) { // 모든 탐색이 끝나고 출력
			System.out.println(Rule.getJSONObject(i).toString());
		}
		return Rule; // JSOANArray 반환

	}
	
	/* XML을 Dom형태로 변환하는 메서드 */
	public static Document loadXMLFromString(String xml) throws Exception { 
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputSource is = new InputSource(new StringReader(xml));
		return builder.parse(is);
	}

	/* RULE_ATTR에서 RULE_NM 추출하는 메서드 */
	public static String rule_name(JSONObject obj) {
		if (obj.has("option")) { // RULE_ATTR에 attribute가 있는 경우
			if (obj.get("type") == "BooleanQuery") { // BooleanQuery의 option을 RULE_NM의 형식에 맞게 변환
				String option = obj.getJSONObject("option").toString();
				option = option.substring(1, option.length() - 1);
				option = option.replaceAll("\"", "");
				option = option.replaceAll("_", "");
				option = option.replaceAll(":", "=");
				return obj.get("type") + "(" + option + ")";
			} else { // Boolean이 아닐 경우
				String option = obj.getJSONObject("option").get(obj.getJSONObject("option").keys().next()).toString(); // option의
																														// value값
				return obj.get("type") + "(" + option + ")";
			}
		} else
			return obj.get("type").toString();
	}

	/* Rule table 데이터 추출하기 위한 재귀적 탐색 메서드 */
	public static void recursive(Node node, JSONArray Rule, String parent_id) {
		JSONObject Rule_row;
		if (node == null || node.getNodeName().equals("#text")) // text node까지 도달
			return;
		Rule_row = new JSONObject();
		JSONObject obj = new JSONObject();

		NamedNodeMap attr = node.getAttributes(); // 현재 node Attribute의 Map
		String content = node.getTextContent();
		obj.put("type", node.getNodeName()); // type에 node의 이름 추가
		JSONObject option = new JSONObject();
		if (attr.getLength() > 0) { // Attribute가 있으면 option에 추가
			for (int k = 0; k < attr.getLength(); k++) {

				option.put("_" + attr.item(k).getNodeName(), attr.item(k).getNodeValue());
			}

			obj.put("option", option);
		} else if (obj.getString("type").equals("TermQuery") || obj.getString("type").equals("SpanTerm")) { // TermQuery나
																											// SpanTerm의
																											// option에
																											// term 추가
			option.put("term", content);
			obj.put("option", option);
		}
		if (node.getNodeType() == Node.TEXT_NODE) { // 현재 node의 type이 text node이면 term 추가
			JSONObject text = new JSONObject();
			text.put("term", content);
			obj.put("option", text);
		}

		Rule_row.put("RULE_ATTR", obj); // RULE_ATTR 추가
		if (obj.getString("type").contains("ConceptQuery")) // 현재 node의 type가 ConceptQuery일 경우 type에 ConceptQeury 추가
			Rule_row.put("RULE_TY", "ConceptQuery");
		else
			Rule_row.put("RULE_TY", "operator"); // 현재 node의 type이 ConceptQuery가 아닐 경우 type에 operator 추가
		Rule_row.put("RULE_NM", rule_name(obj));
		Rule_row.put("RULE_PARENT", parent_id); // 이전 부모 node의 id 추가

		String current_id = UUID.randomUUID().toString();
		Rule_row.put("RULE_ID", current_id); // 현재 node의 ID를 UUID로 Unique하게 설정

		Rule.put(Rule_row); // Rule_row 객체 삽입
		Node node_sibling = node.getNextSibling();
		recursive(node_sibling, Rule, parent_id); // 형제 node 재귀적 탐색
		Node node_child = node.getFirstChild();
		recursive(node_child, Rule, current_id); // 자식 node 재귀적 탐색
	}

	/* taxonomy 파일에서 data를 추출해 doc_concept_rule table 삽입하는 메서드. 재귀적으로 탐색한다.  */
	public static void json_arr_parse(String module_id, JSONArray taxonomy) throws Exception {
		if (taxonomy == null) {
			return;
		} else {
			for (int i = 0; i < taxonomy.length(); i++) { // taxonomy 순회
				JSONObject jsonobj = taxonomy.getJSONObject(i);
				String categoryId = jsonobj.getString("categoryId"); // class_id 추출
				JSONArray query = jsonobj.getJSONArray("queries"); // query 추출
				System.out.println(jsonobj.get("children").toString());
				if (!jsonobj.get("children").toString().equals("null")) { // children 있으면 재귀적 탐색
					json_arr_parse(module_id, jsonobj.getJSONArray("children"));
				} else {
					JSONArray Rule = getJSON(query.get(0).toString());

					for (int j = 0; j < Rule.length(); j++) { // Rule에서 DB에 전송할 query 데이터 추출
						String Rule_parent = Rule.getJSONObject(j).getString("RULE_PARENT");
						String Rule_name = Rule.getJSONObject(j).getString("RULE_NM");
						JSONObject Rule_attr = Rule.getJSONObject(j).getJSONObject("RULE_ATTR");
						String Rule_id = Rule.getJSONObject(j).getString("RULE_ID");
						String Rule_ty = Rule.getJSONObject(j).getString("RULE_TY");
						String Rule_query = "INSERT INTO doc_concept_rule" + " VALUES ('" + module_id + "', '"
								+ categoryId + "', '" + Rule_id + "', '" + Rule_name + "', '" + Rule_parent + "', '"
								+ Rule_ty + "', '" + Rule_attr.toString() + "')";

						DB_Connection(Rule_query); // DB에 query 전송
					}

				}
			}

		}
	}

	/* doc_concept_class table 삽입 메서드 */
	public static void insert_class(String module_id, JSONArray taxonomy) throws IOException { 
		if (module_id == null)
			return;
		else {
			for (int i = 0; i < taxonomy.length(); i++) { // taxonomy 순회
				JSONObject jsonobj = taxonomy.getJSONObject(i);
				String categoryId = jsonobj.getString("categoryId"); // CLASS_ID 추출
				String categoryName = jsonobj.getString("categoryName"); // CLASS_NM 추출
				if (!jsonobj.has(("CLASS_PARENT"))) { // root object의 CLASS_PARENT를 #으로 설정
					jsonobj.put("CLASS_PARENT", "#");
				}

				if (!jsonobj.get("children").toString().equals("null")) { // children 있으면 재귀적 탐색
					for (int j = 0; j < jsonobj.getJSONArray("children").length(); j++)
						jsonobj.getJSONArray("children").getJSONObject(j).put("CLASS_PARENT", categoryId); // children 탐색 전 CLASS_PARENT 삽입
					insert_class(module_id, jsonobj.getJSONArray("children"));
				}

				String class_parent = jsonobj.getString("CLASS_PARENT"); // 현재 노드의 CLASS_PARENT 추출
				String query = "INSERT INTO doc_concept_class" + " VALUES ('" + module_id + "', '" + categoryId + "', '"
						+ categoryName + "', '" + class_parent + "');";
				DB_Connection(query); // DB에 query 전송

			}
		}
	}

	/* doc_cencept_component table 삽입 메서드 */
	public static void insert_component(String module_id, String concept_path) throws IOException {
		if (module_id == null)
			return;
		else {
			File concept_file = new File(concept_path);
			String line = null;
			String component_name = null;
			int index = concept_file.getName().lastIndexOf(".");
			if (index != -1)
				component_name = concept_file.getName().substring(0, index); // concept file의 이름 추출
	
			BufferedReader bufReader = new BufferedReader(
					new InputStreamReader(new FileInputStream(concept_file), "UTF-8")); // UTF-8 포맷으로 concept file 내용 추출
			while ((line = bufReader.readLine()) != null) {
				String component_term = line;
				String query = "INSERT INTO doc_concept_component " + "VALUES (" + "'" + module_id + "', '"
						+ UUID.randomUUID().toString() + "', '" + component_name + "', '" + component_term + "', "
						+ "null" + ")";
				DB_Connection(query); // DB에 query 전송

			}

		}
	}

	
	/* DB와 연결하여 query 수행하는 메서드 */
	public static void DB_Connection(String Query) {
		Connection connection = null;
		Statement st = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection( // DB와 연결
					"jdbc:mysql://192.168.210.167:8540?serverTimezone=UTC&autoReconnect=true", "bigone", "bigone123");
			st = connection.createStatement();
			st.executeUpdate("use BIGSTATION_UPT;"); // DB 선택
		
			int i = st.executeUpdate(Query); // query 전송 및 수행
			System.out.println("i:" + i);

		} catch (SQLException se1) {
			se1.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (st != null)
					st.close();
			} catch (SQLException se2) {
				se2.printStackTrace();
			}
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
	}

}