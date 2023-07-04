package io.metersphere.system.controller;

import com.jayway.jsonpath.JsonPath;
import io.metersphere.sdk.constants.SessionConstants;
import io.metersphere.sdk.controller.handler.ResultHolder;
import io.metersphere.sdk.dto.BasePageRequest;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.Pager;
import io.metersphere.system.domain.AuthSource;
import io.metersphere.system.request.AuthSourceRequest;
import io.metersphere.utils.JsonUtils;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthSourceControllerTest {

    @Resource
    private MockMvc mockMvc;

    private static String sessionId;
    private static String csrfToken;

    public static final String AUTH_SOURCE_ADD = "/system/authsource/add";

    public static final String AUTH_SOURCE_List = "/system/authsource/list";

    public static final String AUTH_SOURCE_UPDATE = "/system/authsource/update";

    public static final String AUTH_SOURCE_GET = "/system/authsource/get/";

    public static final String AUTH_SOURCE_DELETE = "/system/authsource/delete/";

    private static final ResultMatcher CLIENT_ERROR_MATCHER = status().is4xxClientError();

    @BeforeEach
    public void login() throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/login")
                        .content("{\"username\":\"admin\",\"password\":\"metersphere\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        sessionId = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.sessionId");
        csrfToken = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.csrfToken");
    }


    @Test
    @Order(1)
    public void testAddSource() throws Exception {
        AuthSourceRequest authSource = new AuthSourceRequest();
        authSource.setConfiguration("123");
        authSource.setName("测试CAS");
        authSource.setType("CAS");
        this.requestPost(AUTH_SOURCE_ADD, authSource);

    }

    @Test
    @Order(2)
    public void testGetSourceList() throws Exception {
        BasePageRequest basePageRequest = new BasePageRequest();
        basePageRequest.setCurrent(1);
        basePageRequest.setPageSize(10);
        this.requestPost(AUTH_SOURCE_List, basePageRequest);
    }


    @Test
    @Order(3)
    public void testUpdateSource() throws Exception {
        List<AuthSourceRequest> authSourceList = this.getAuthSourceList();
        AuthSourceRequest authSource = new AuthSourceRequest();
        authSource.setId(authSourceList.get(0).getId());
        authSource.setConfiguration("123666");
        authSource.setName("更新");
        authSource.setType("CAS");
        this.requestPost(AUTH_SOURCE_UPDATE, authSource);
    }

    @Test
    @Order(4)
    public void testUpdateStatus() throws Exception {
        List<AuthSourceRequest> authSourceList = this.getAuthSourceList();
        this.requestGet(AUTH_SOURCE_UPDATE + "/" + authSourceList.get(0).getId() + "/status/false");
    }


    @Test
    @Order(5)
    public void testGetSourceById() throws Exception {
        List<AuthSourceRequest> authSourceList = this.getAuthSourceList();
        this.requestGet(AUTH_SOURCE_GET + authSourceList.get(0).getId());
    }


    @Test
    @Order(6)
    public void testDelSourceById() throws Exception {
        List<AuthSourceRequest> authSourceList = this.getAuthSourceList();
        this.requestGet(AUTH_SOURCE_DELETE + authSourceList.get(0).getId());
    }


    @Test
    @Order(7)
    public void testAddSourceError() throws Exception {
        AuthSource authSource = new AuthSource();
        authSource.setConfiguration("123".getBytes());
        this.requestPost(AUTH_SOURCE_ADD, authSource, CLIENT_ERROR_MATCHER);
    }


    private MvcResult requestPost(String url, Object param) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .header(SessionConstants.HEADER_TOKEN, sessionId)
                        .header(SessionConstants.CSRF_TOKEN, csrfToken)
                        .content(JSON.toJSONString(param))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andReturn();
    }

    private MvcResult requestGet(String url) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(url)
                        .header(SessionConstants.HEADER_TOKEN, sessionId)
                        .header(SessionConstants.CSRF_TOKEN, csrfToken))
                .andExpect(status().isOk()).andDo(print()).andReturn();
    }

    private List<AuthSourceRequest> getAuthSourceList() throws Exception {
        BasePageRequest basePageRequest = new BasePageRequest();
        basePageRequest.setCurrent(1);
        basePageRequest.setPageSize(10);
        MvcResult mvcResult = this.requestPost(AUTH_SOURCE_List, basePageRequest);
        String returnData = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        ResultHolder resultHolder = JsonUtils.parseObject(returnData, ResultHolder.class);
        Pager<?> returnPager = JSON.parseObject(JSON.toJSONString(resultHolder.getData()), Pager.class);
        List<AuthSourceRequest> authSourceRequests = JSON.parseArray(JSON.toJSONString(returnPager.getList()), AuthSourceRequest.class);
        return authSourceRequests;
    }

    private void requestPost(String url, Object param, ResultMatcher resultMatcher) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .header(SessionConstants.HEADER_TOKEN, sessionId)
                        .header(SessionConstants.CSRF_TOKEN, csrfToken)
                        .content(JSON.toJSONString(param))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(resultMatcher).andDo(print())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}