package com.yourcompany.common.base.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据库实体统一基类。
 *
 * <p>所有数据库实体必须继承本类，统一携带主键、逻辑删除、乐观锁、创建/修改时间。
 * 字段填充依赖 MyBatis-Plus 的 MetaObjectHandler，需自行注册。
 *
 * <p>约定：
 * <ul>
 *   <li>实体使用 @Getter/@Setter，不使用 @Data</li>
 *   <li>实体不得添加 Spring 容器注解（@Component 等）</li>
 *   <li>非持久化字段必须标注 @TableField(exist = false) 并注释说明</li>
 * </ul>
 */
@Getter
@Setter
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 逻辑删除：0 未删除，1 已删除
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer lockVersion;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date gmtCreate;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date gmtModified;
}
