package com.wzz.smscode.service.impl;

import com.wzz.smscode.dto.agent.AgentProjectLineUpdateDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.UserProjectLineService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplSecurityTest {

    @Test
    void agentUpdateUsesExistingProjectLineAndDoesNotRebind() {
        UserServiceImpl userService = spy(new UserServiceImpl());
        UserProjectLineService lineService = mock(UserProjectLineService.class);
        ProjectService projectService = mock(ProjectService.class);
        ReflectionTestUtils.setField(userService, "userProjectLineService", lineService);
        ReflectionTestUtils.setField(userService, "projectService", projectService);

        User agent = new User();
        agent.setId(10L);
        agent.setIsAgent(1);
        User owner = new User();
        owner.setId(20L);
        owner.setParentId(10L);
        UserProjectLine line = new UserProjectLine();
        line.setId(99L);
        line.setUserId(20L);
        line.setProjectId("105");
        line.setLineId("1");
        line.setCostPrice(new BigDecimal("1.00"));
        Project project = new Project();
        project.setPriceMax(new BigDecimal("10.00"));

        doReturn(agent).when(userService).getById(10L);
        doReturn(owner).when(userService).getById(20L);
        when(lineService.getById(99L)).thenReturn(line);
        when(lineService.updateById(line)).thenReturn(true);
        when(projectService.getProject("105", 1)).thenReturn(project);

        AgentProjectLineUpdateDTO dto = new AgentProjectLineUpdateDTO();
        dto.setUserProjectLineId(99L);
        dto.setAgentPrice(new BigDecimal("2.00"));
        dto.setProjectId("999");
        dto.setLineId("9");
        dto.setRemark("ok");

        userService.updateAgentProjectConfig(10L, dto);

        assertEquals("105", line.getProjectId());
        assertEquals("1", line.getLineId());
        assertEquals(new BigDecimal("2.00"), line.getAgentPrice());
        assertEquals("ok", line.getRemark());
        verify(projectService).getProject("105", 1);
        verify(lineService).updateById(line);
    }
}
