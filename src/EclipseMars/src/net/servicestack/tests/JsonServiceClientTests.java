package net.servicestack.tests;

import static org.junit.Assert.*;
import static net.servicestack.tests.test.*;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import net.servicestack.client.ConnectionFilter;
import net.servicestack.client.ExceptionFilter;
import net.servicestack.client.HttpMethods;
import net.servicestack.client.JsonServiceClient;
import net.servicestack.client.Log;
import net.servicestack.client.MimeTypes;
import net.servicestack.client.ResponseStatus;
import net.servicestack.client.TimeSpan;
import net.servicestack.client.Utils;
import net.servicestack.client.WebServiceException;

public class JsonServiceClientTests {
	
	JsonServiceClient client = new JsonServiceClient("http://test.servicestack.net");

//	@BeforeClass 
//    public static void setUpClass() {      
//		JsonServiceClient.GlobalRequestFilter = new ConnectionFilter() {
//		    @Override public void exec(HttpURLConnection conn) {
//		        String verb = conn.getRequestMethod();
//		        if (verb == HttpMethods.Post || verb == HttpMethods.Put) {
//		            conn.setDoOutput(true);
//		        }
//		    }
//		};
//	}

	@Test
	public void test_Can_GET_Hello() {
		Hello request = new Hello().setName("World");

		HelloResponse response = client.get(request);

		assertEquals("Hello, World!", response.getResult());
	}

	@Test
	public void test_does_fire_Request_and_Response_Filters() {

		JsonServiceClient client = new JsonServiceClient("http://test.servicestack.net");

		final ArrayList<String> events = new ArrayList<>();

		JsonServiceClient.GlobalRequestFilter = new ConnectionFilter() {
			@Override
			public void exec(HttpURLConnection conn) {
				events.add("GlobalRequestFilter");
		        String verb = conn.getRequestMethod();
		        if (verb == HttpMethods.Post || verb == HttpMethods.Put) {
		            conn.setDoOutput(true);
		        }
			}
		};
		JsonServiceClient.GlobalResponseFilter = new ConnectionFilter() {
			@Override
			public void exec(HttpURLConnection conn) {
				events.add("GlobalResponseFilter");
			}
		};

		client.RequestFilter = new ConnectionFilter() {
			@Override
			public void exec(HttpURLConnection conn) {
				events.add("RequestFilter");
			}
		};
		client.ResponseFilter = new ConnectionFilter() {
			@Override
			public void exec(HttpURLConnection conn) {
				events.add("ResponseFilter");
			}
		};

		Hello request = new Hello().setName("World");

		HelloResponse response = client.get(request);

		assertEquals("Hello, World!", response.getResult());

//		String results = TextUtils.join(", ", events);
//
//		assertEquals("RequestFilter, GlobalRequestFilter, ResponseFilter, GlobalResponseFilter", results);
	}

	@Test
	public void test_Can_GET_Hello_with_CustomPath() {
		HelloResponse response = client.get("/hello/World", HelloResponse.class);

		assertEquals("Hello, World!", response.getResult());
	}

	@Test
	public void test_Can_POST_Hello_with_CustomPath() {
		HelloResponse response = client.post("/hello", new Hello().setName("World"), HelloResponse.class);

		assertEquals("Hello, World!", response.getResult());
	}

	@Test
	public void test_Can_GET_Hello_with_CustomPath_raw() {
		HttpURLConnection response = client.get("/hello/World");
		String json = Utils.readToEnd(response);

		assertEquals("{\"result\":\"Hello, World!\"}", json);
	}

	@Test
	public void test_Can_POST_Hello_with_CustomPath_raw() {
		HttpURLConnection response = client.post("/hello", Utils.toUtf8Bytes("Name=World"), MimeTypes.FormUrlEncoded);
		String json = Utils.readToEnd(response);

		assertEquals("{\"result\":\"Hello, World!\"}", json);
	}

	@Test
	public void test_Can_POST_test_HelloAllTypes() {
		
        JsonServiceClient.GlobalRequestFilter = new ConnectionFilter() {
            @Override public void exec(HttpURLConnection conn) {
                String verb = conn.getRequestMethod();
                if (verb == HttpMethods.Post || verb == HttpMethods.Put) {
                    conn.setDoOutput(true);
                }
            }
        };
		
		
		HelloAllTypes request = createHelloAllTypes();
		HelloAllTypesResponse response = client.post(request);
		assertHelloAllTypesResponse(response, request);
	}

	@Test
	public void test_Can_PUT_test_HelloAllTypes() {
		HelloAllTypes request = createHelloAllTypes();
		HelloAllTypesResponse response = client.put(request);
		assertHelloAllTypesResponse(response, request);
	}

	@Test
	public void test_Can_Serailize_AllTypes() {
		String json = client.getGson().toJson(createAllTypes());
		Log.i(json);
	}

	@Test
	public void test_Does_handle_404_Error() {
		JsonServiceClient testClient = new JsonServiceClient("http://test.servicestack.net");

		final Exception[] globalError = new Exception[1]; // Wow Java, you suck.
		final Exception[] localError = new Exception[1];
		WebServiceException thrownError = null;

		JsonServiceClient.GlobalExceptionFilter = new ExceptionFilter() {
			@Override
			public void exec(HttpURLConnection res, Exception ex) {
				globalError[0] = ex;
			}
		};

		testClient.ExceptionFilter = new ExceptionFilter() {
			@Override
			public void exec(HttpURLConnection res, Exception ex) {
				localError[0] = ex;
			}
		};

		ThrowType request = new ThrowType().setType("NotFound").setMessage("not here");

		try {
			ThrowTypeResponse response = testClient.put(request);
		} catch (WebServiceException webEx) {
			thrownError = webEx;
		}

		assertNotNull(globalError[0]);
		assertNotNull(localError[0]);
		assertNotNull(thrownError);

		ResponseStatus status = thrownError.getResponseStatus();

		assertEquals("not here", status.getErrorCode());
		assertEquals("not here", status.getMessage());
		assertNotNull(status.getStackTrace());
	}

	@Test
	public void test_Does_handle_ValidationException() {
		ThrowValidation request = new ThrowValidation().setEmail("invalidemail");

		try {
			client.post(request);
			fail("Should throw");
		} catch (WebServiceException webEx) {
			ResponseStatus status = webEx.getResponseStatus();

			assertNotNull(status);
			assertEquals(3, status.getErrors().size());

			assertEquals(status.getErrors().get(0).getErrorCode(), status.getErrorCode());
			assertEquals(status.getErrors().get(0).getMessage(), status.getMessage());

			assertEquals("InclusiveBetween", status.getErrors().get(0).getErrorCode());
			assertEquals("'Age' must be between 1 and 120. You entered 0.", status.getErrors().get(0).getMessage());
			assertEquals("Age", status.getErrors().get(0).getFieldName());

			assertEquals("NotEmpty", status.getErrors().get(1).errorCode);
			assertEquals("'Required' should not be empty.", status.getErrors().get(1).getMessage());
			assertEquals("Required", status.getErrors().get(1).getFieldName());

			assertEquals("Email", status.getErrors().get(2).errorCode);
			assertEquals("'Email' is not a valid email address.", status.getErrors().get(2).getMessage());
			assertEquals("Email", status.getErrors().get(2).getFieldName());
		}
	}

	@Test
	public void test_Can_POST_valid_ThrowValidation_request() {
		ThrowValidation request = new ThrowValidation().setAge(21).setRequired("foo").setEmail("my@gmail.com");

		ThrowValidationResponse response = client.post(request);

		assertNotNull(response);
		assertEquals(request.getAge(), response.getAge());
		assertEquals(request.getRequired(), response.getRequired());
		assertEquals(request.getEmail(), response.getEmail());
	}

//	public void test_does_handle_auth_failure() {
//		JsonServiceClient techStacksClient = new JsonServiceClient("http://techstacks.io/");
//		int errorCode = 0;
//		try {
//			dto.LockTechStack request = new dto.LockTechStack();
//			request.setTechnologyStackId((long) 6);
//			dto.LockStackResponse res = techStacksClient.post(request);
//			fail("Should throw");
//		} catch (WebServiceException ex) {
//			// private StatusCode has correct code, response status is null due
//			// to empty response body.
//			errorCode = ex.getStatusCode();
//		}
//		assertEquals(errorCode, 401);
//	}
	
	/* TEST HELPERS */

	public static HelloAllTypes createHelloAllTypes() {
		HelloAllTypes to = new HelloAllTypes().setName("name").setAllTypes(createAllTypes())
				.setAllCollectionTypes(createAllCollectionTypes());
		return to;
	}

	public static void assertHelloAllTypesResponse(HelloAllTypesResponse actual, HelloAllTypes expected) {
		assertNotNull(actual);
		assertAllTypes(actual.allTypes, expected.allTypes);
		assertAllCollectionTypes(actual.allCollectionTypes, expected.allCollectionTypes);
	}

	public static AllTypes createAllTypes() {
		AllTypes to = new AllTypes().setId(1).setChar("c").setByte((short) 2).setShort((short) 3).setInt(4)
				.setLong((long) 5).setUShort(6).setUInt((long) 7).setULong((BigInteger.valueOf(8)))
				.setFloat((float) 1.1).setDouble(2.2).setDecimal(new BigDecimal("3.0")).setString("string")
				.setDateTime(new Date(101, 0, 1)).setDateTimeOffset(new Date(101, 0, 1))
				.setTimeSpan(new TimeSpan().addHours(1)).setGuid(UUID.randomUUID())
				.setStringList(Utils.createList("A", "B", "C")).setStringArray(Utils.createList("D", "E", "F"))
				.setStringMap(Utils.createMap("A", "D", "B", "E", "C", "F"))
				.setIntStringMap(Utils.createMap(1, "A", 2, "B", 3, "C"))
				.setSubType(new SubType().setId(1).setName("name"));

		return to;
	}

	public static AllCollectionTypes createAllCollectionTypes() {
		AllCollectionTypes to = new AllCollectionTypes().setIntArray(Utils.createList(1, 2, 3))
				.setIntList(Utils.createList(4, 5, 6)).setStringArray(Utils.createList("A", "B", "C"))
				.setStringList(Utils.createList("D", "E", "F")).setPocoArray(Utils.createList(createPoco("pocoArray")))
				.setPocoList(Utils.createList(createPoco("pocoList")))
				.setPocoLookup(Utils.createMap("A", Utils.createList(createPoco("B"), createPoco("C"))))
				.setPocoLookupMap(Utils.createMap("A", Utils.createList(Utils.createMap("B", createPoco("C")),
						Utils.createMap("D", createPoco("E")))));
		return to;
	}

	public static Poco createPoco(String name) {
		return new Poco().setName(name);
	}

	public static void assertAllTypes(AllTypes actual, AllTypes expected) {
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getByte(), actual.getByte());
		assertEquals(expected.getShort(), actual.getShort());
		assertEquals(expected.getInt(), actual.getInt());
		assertEquals(expected.getLong(), actual.getLong());
		assertEquals(expected.getUShort(), actual.getUShort());
		assertEquals(expected.getULong(), actual.getULong());
		assertEquals(expected.getFloat(), actual.getFloat());
		assertEquals(expected.getDouble(), actual.getDouble());
		assertEquals(expected.getDecimal(), actual.getDecimal());
		assertEquals(expected.getString(), actual.getString());
//		assertEquals(expected.getDateTime(), actual.getDateTime());
		assertEquals(expected.getTimeSpan(), actual.getTimeSpan());
		assertEquals(expected.getGuid(), actual.getGuid());
		assertEquals(expected.getChar(), actual.getChar());
		assertEquals(expected.getStringArray(), actual.getStringArray());
		assertEquals(expected.getStringList(), actual.getStringList());

		assertEquals(expected.getStringMap(), actual.getStringMap());
		assertEquals(expected.getIntStringMap(), actual.getIntStringMap());

		assertEquals(expected.getSubType().getId(), actual.getSubType().getId());
		assertEquals(expected.getSubType().getName(), actual.getSubType().getName());
	}

	public static void assertAllCollectionTypes(AllCollectionTypes actual, AllCollectionTypes expected) {
		assertEquals(expected.getIntArray(), actual.getIntArray());
		assertEquals(expected.getIntList(), actual.getIntList());
		assertEquals(expected.getStringArray(), actual.getStringArray());
		assertEquals(expected.getStringList(), actual.getStringList());
		assertPocoEquals(expected.getPocoArray(), actual.getPocoArray());
		assertPocoEquals(expected.getPocoList(), actual.getPocoList());

		assertPocoLookupEquals(expected.getPocoLookup(), actual.getPocoLookup());
		assertPocoLookupMapEquals(expected.getPocoLookupMap(), actual.getPocoLookupMap());
	}

	public static void assertPocoEquals(List<Poco> expected, List<Poco> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < actual.size(); i++) {
			assertPocoEquals(expected.get(i), actual.get(i));
		}
	}

	public static void assertPocoLookupEquals(HashMap<String, ArrayList<Poco>> expected,
			HashMap<String, ArrayList<Poco>> actual) {
		assertEquals(expected.size(), actual.size());
		for (String key : actual.keySet()) {
			assertPocoEquals(expected.get(key), actual.get(key));
		}
	}

	public static void assertPocoLookupMapEquals(HashMap<String, ArrayList<HashMap<String, Poco>>> expected,
			HashMap<String, ArrayList<HashMap<String, Poco>>> actual) {
		assertEquals(expected.size(), actual.size());
		for (String key : actual.keySet()) {
			assertPocoEquals(expected.get(key), actual.get(key));
		}
	}

	public static void assertPocoEquals(ArrayList<HashMap<String, Poco>> expected,
			ArrayList<HashMap<String, Poco>> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < actual.size(); i++) {
			assertPocoEquals(expected.get(i), actual.get(i));
		}
	}

	public static void assertPocoEquals(HashMap<String, Poco> expected, HashMap<String, Poco> actual) {
		assertEquals(expected.size(), actual.size());
		for (String key : actual.keySet()) {
			assertPocoEquals(expected.get(key), actual.get(key));
		}
	}

	public static void assertPocoEquals(Poco expected, Poco actual) {
		assertNotNull(actual);
		assertEquals(actual.getName(), expected.getName());
	}

}
