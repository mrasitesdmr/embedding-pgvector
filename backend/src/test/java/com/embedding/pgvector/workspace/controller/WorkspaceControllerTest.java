package com.embedding.pgvector.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.workspace.domain.dto.WorkspaceResponse;
import com.embedding.pgvector.workspace.service.WorkspaceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * WorkspaceController'ın sayfa parametresi doğrulamasını, varsayılan sayfa boyutunu ve 404 ile 400
 * yanıtlarını sahte servisle doğrular.
 */
@WebMvcTest(WorkspaceController.class)
class WorkspaceControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private WorkspaceService workspaceService;

    @ParameterizedTest
    @CsvSource({"page=-1,page,must be greater than or equal to 0", "size=0,size,must be between 1 and 100",
            "size=101,size,must be between 1 and 100"})
    void givenInvalidPageOrSize_whenListWorkspaces_thenReturns400ValidationFailed(String query, String field,
            String message) {
        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces?" + query).exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo(message);
        then(workspaceService).should(never()).listWorkspaces(anyInt(), anyInt());
    }

    @Test
    void givenNoParameters_whenListWorkspaces_thenDefaultsToPageZeroSizeFifty() {
        // Arrange
        WorkspaceResponse workspace = new WorkspaceResponse(42L, "Belgelerim", 4, 42L, java.util.List.of("bge-m3@1024"));
        given(workspaceService.listWorkspaces(0, 50))
                .willReturn(new PageImpl<>(List.of(workspace), PageRequest.of(0, 50), 1));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].workspaceId").isEqualTo(42);
        assertThat(result).bodyJson().extractingPath("$.content[0].sourceCount").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.content[0].chunkCount").isEqualTo(42);
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(50);
    }

    @Test
    void givenUnknownWorkspace_whenGetWorkspace_thenReturns404WorkspaceNotFound() {
        // Arrange
        given(workspaceService.getWorkspace(999L)).willThrow(new NotFoundException("WORKSPACE_NOT_FOUND", "x"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/999").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("WORKSPACE_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/workspace-not-found");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Not Found");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/workspaces/999");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
    }

    @Test
    void givenNonNumericWorkspaceId_whenGetWorkspace_thenReturns400BadRequest() {
        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/abc").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("BAD_REQUEST");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/bad-request");
    }
}
