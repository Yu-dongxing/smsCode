package com.wzz.smscode.dto.project;

import com.wzz.smscode.dto.ApiConfig.ApiConfig;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.enums.AuthType;
import com.wzz.smscode.enums.RequestType;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;

/**
 * 项目配置增删改入参，只暴露允许前端写入的项目配置字段。
 */
@Data
public class ProjectMutationDTO {
    private Long id;
    private String projectId;
    private String projectName;
    private String lineName;
    private String lineId;
    private String domain;
    private String selectNumberApiRoute;
    private String selectNumberApiRouteMethod;
    private RequestType selectNumberApiReauestType;
    private AuthType authType;
    private String selectNumberApiRequestValue;
    private String responseSelectNumberApiField;
    private Integer codeTimeout;
    private Integer codeMaxAttempts;
    private BigDecimal costPrice;
    private BigDecimal priceMax;
    private BigDecimal priceMin;
    private boolean status;
    private Boolean enableFilter;
    private String filterId;
    private ApiConfig loginConfig;
    private ApiConfig getNumberConfig;
    private ApiConfig getCodeConfig;
    private ApiConfig getBalanceConfig;
    private ApiConfig deletePhoneConfig;
    private String releaseSuccessStatus;
    private String releaseFailStatus;
    private String releaseSuccessMsg;
    private String releaseFailMsg;
    private String projectInfo;
    private Boolean specialApiStatus;
    private Integer specialApiDelay;
    private Integer specialApiGetCodeOutTime;
    private String specialApiToken;
    private String specialApiHost;
    private Boolean outsideOrderApiStatus;
    private String outsideOrderApiHost;
    private String outsideOrderApiUserId;
    private Integer outsideOrderPollIntervalMs;
    private Integer outsideOrderFeedbackRetryIntervalMs;
    private Integer outsideOrderFeedbackMaxAttempts;
    private Boolean aesSpecialApiStatus;
    private String aesSpecialApiGateway;
    private String aesSpecialApiOutNumber;
    private String aesSpecialApiKey;
    private String aesSpecialApiProjectName;
    private Boolean enableRateBan;
    private BigDecimal minRateThreshold;
    private Integer minAttemptsThreshold;
    private BigDecimal banDurationHours;

    public Project toProject() {
        Project project = new Project();
        BeanUtils.copyProperties(this, project);
        return project;
    }
}
