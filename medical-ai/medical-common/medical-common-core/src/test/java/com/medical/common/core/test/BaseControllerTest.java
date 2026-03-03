package com.medical.common.core.test;

/**
 * Controller test checklist (for @WebMvcTest):
 * 1) Use @WebMvcTest(YourController.class)
 * 2) Use @AutoConfigureMockMvc(addFilters = false)
 * 3) Mock all controller dependencies with @MockBean
 * 4) Use @ActiveProfiles("test") to load application-test.yml
 * 5) Mock static auth helpers (SecurityUtil/StpUtil) where needed
 */
public final class BaseControllerTest {

    private BaseControllerTest() {
        // utility holder
    }
}
