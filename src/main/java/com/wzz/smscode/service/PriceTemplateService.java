package com.wzz.smscode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.PriceTemplateCreateDTO;
import com.wzz.smscode.dto.PriceTemplateResponseDTO;
import com.wzz.smscode.entity.PriceTemplate;
import com.wzz.smscode.entity.PriceTemplateItem;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PriceTemplateService extends IService<PriceTemplate> {

    @Transactional(rollbackFor = Exception.class)
    boolean saveOrUpdateTemplate(PriceTemplateCreateDTO dto, Long operatorId);

    /**
     * 创建模板，并指定创建者ID
     * @param createDTO 模板信息
     * @param creatorId 创建者ID (管理员为0, 代理为自身ID)
     * @return 是否成功
     */
    boolean createTemplate(PriceTemplateCreateDTO createDTO, Long creatorId);

    /**
     * 根据创建者ID列出其所有模板
     * @param creatorId 创建者ID
     * @return 模板列表
     */
    List<PriceTemplateResponseDTO> listTemplatesByCreator(Long creatorId);

    /**
     * 更新模板，并校验操作者权限
     * @param templateId 模板ID
     * @param updateDTO  更新信息
     * @param operatorId 操作者ID，必须与模板的creatorId匹配
     * @return 是否成功
     */
    boolean updateTemplate(Long templateId, PriceTemplateCreateDTO updateDTO, Long operatorId);

    /**
     * 删除模板，并校验操作者权限
     * @param templateId 模板ID
     * @param operatorId 操作者ID，必须与模板的creatorId匹配
     * @return 是否成功
     */
    boolean deleteTemplate(Long templateId, Long operatorId);

    PriceTemplateItem getPriceConfig(Long templateId, String projectId, Integer lineId);

    void addUserToTemplate(Long templateId, Long userId);

    void removeUserFromTemplate(Long templateId, Long userId);

    List<PriceTemplateItem> getTemplateItems(Long templateId);
}