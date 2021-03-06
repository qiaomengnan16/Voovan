package org.voovan.db.recorder.annotation;

import java.lang.annotation.*;

/**
 * 类文字命名
 *
 * @author: helyho
 * DBase Framework.
 * WebSite: https://github.com/helyho/DBase
 * Licence: Apache v2 License
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Table {
    String database() default "";
    String name() default "";
    String value() default "";
    boolean lowerCase() default false;
    boolean upperCase() default false;
    boolean upperCaseHead() default false;
}
