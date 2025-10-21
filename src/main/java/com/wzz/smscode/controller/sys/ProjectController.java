package com.wzz.smscode.controller.sys;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.common.Result;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.service.ProjectService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.*;

/**
 * 项目接口控制器
 */
@RestController
@RequestMapping("/api/project")
public class ProjectController {
    private static final Logger log = LogManager.getLogger(ProjectController.class);
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 查询所有项目，支持分页。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页显示的记录数
     * @return 包含查询结果的Result对象。如果查询成功且有数据，则返回包含项目列表的结果；如果查询结果为空，则返回错误信息"列表为空"。
     */
    @GetMapping("/find/all")
    public Result<?> findAll(@RequestParam(required = false) Long pageNum,
                             @RequestParam(required = false) Long pageSize) {

        // 1. 参数为空 → 查全部
        if (pageNum == null || pageSize == null) {
            pageNum = 1L;
            pageSize = -1L;          // -1 表示不分页，MP 会查全部
        }

        Page<Project> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Project> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(Project::getCreateTime);

        IPage<Project> projectIPage = projectService.page(page, queryWrapper);

        return projectIPage.getRecords().isEmpty()
                ? Result.error("列表为空")
                : Result.success("查询成功", projectIPage);
    }

    /**
     * 更新指定的项目信息。
     *
     * @param project 项目实体对象，包含需要更新的信息
     * @return 返回一个Result对象。如果更新成功，则返回成功消息"更新成功"；如果更新失败，则返回错误消息"更新失败"。
     */
    @PostMapping("/update")
    public Result<?> updateByProject(@RequestBody Project project){
        log.info("传入数据：{}",project);

        boolean is = projectService.updateById(project);
        if (is){
            return Result.success("更新成功");
        }
        return Result.error("更新失败");
    }

    /**
     * 根据指定ID删除项目。(ID不是项目id)，
     *
     * @param id 需要删除的项目的ID
     * @return 返回一个Result对象。如果删除成功，则返回成功消息"删除成功"；如果删除失败，则返回错误消息"删除失败"或具体的业务异常信息。
     */
    @PostMapping("/delete/by-id/{id}")
    public Result<?> deleteById(@PathVariable("id") long id){
        try{
            Boolean is = projectService.deleteByID(id);
            if (is){
                return Result.success("删除成功");
            }
            return Result.error("删除失败");
        } catch (BusinessException e){
            return Result.error(e.getMessage());
        }
    }

    /**
     * 添加一个新的项目。
     *
     * @param project 项目实体对象，包含需要添加的项目信息
     * @return 返回一个Result对象。如果添加成功，则返回成功消息"添加成功"；如果添加失败，则返回错误消息"添加失败"或具体的业务异常信息。
     */
    @PostMapping("/add")
    public Result<?> add(@RequestBody Project project){
        try{
            Boolean is  =  projectService.save(project);
            if (is){
                return Result.success("添加成功");
            }
            return Result.error("添加失败！");
        }catch (BusinessException e){
            return Result.error(e.getMessage());
        }
    }
}
