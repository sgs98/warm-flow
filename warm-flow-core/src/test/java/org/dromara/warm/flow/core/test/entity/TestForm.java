package org.dromara.warm.flow.core.test.entity;

import lombok.Data;
import lombok.experimental.Accessors;
import org.dromara.warm.flow.core.entity.Form;

import java.util.Date;

/**
 * 单测内存实体：流程表单。
 *
 * @author warm
 */
@Data
@Accessors(chain = true)
public class TestForm implements Form {

    private Long id;

    private Date createTime;

    private Date updateTime;

    private String createBy;

    private String updateBy;

    private String tenantId;

    private String delFlag;

    private String formCode;

    private String formName;

    private String version;

    private Integer isPublish;

    private Integer formType;

    private String formContent;

    private String formPath;

    private String ext;
}
