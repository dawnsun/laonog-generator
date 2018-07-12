package com.laonog.generator.utils;

import com.laonog.generator.entity.ColumnEntity;
import com.laonog.generator.entity.TableEntity;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器   工具类
 *
 */
public class GeneratorUtils {

    public static List<String> getTemplates() {
        List<String> templates = new ArrayList<String>();
        templates.add("template/index.js.vm");
        templates.add("template/index.vue.vm");
        templates.add("template/mapper.xml.vm");
        templates.add("template/service.java.vm");
        templates.add("template/serviceimpl.java.vm");
        templates.add("template/entity.java.vm");
        templates.add("template/dao.java.vm");
        templates.add("template/controller.java.vm");
        templates.add("template/query.java.vm");
        templates.add("template/converter.java.vm");
        templates.add("template/vo.java.vm");
        return templates;
    }

    /**
     * 生成代码
     */
    public static void generatorCode(Map<String, String> table,
                                     List<Map<String, String>> columns, ZipOutputStream zip) {
        //配置信息
        Configuration config = getConfig();

        //表信息
        TableEntity tableEntity = new TableEntity();
        tableEntity.setTableName(table.get("tableName"));
        tableEntity.setComments(table.get("tableComment"));
        //表名转换成Java类名
        String className = tableToJava(tableEntity.getTableName(), config.getString("tablePrefix"));
        String classQueryName = className+"Query";
        tableEntity.setClassName(className);
        tableEntity.setClassname(StringUtils.uncapitalize(className));
        //所有的列明和代值
        String allColumnName = "";
        String allColumnValue="";
        //批量列表对象查询循环数据
        String allColumnValueList="";
        //列信息
        List<ColumnEntity> columsList = new ArrayList<>();
        for (Map<String, String> column : columns) {
            ColumnEntity columnEntity = new ColumnEntity();
            columnEntity.setColumnName(column.get("columnName"));
            if("datetime".equals(column.get("dataType"))){
                columnEntity.setDataType("TIMESTAMP");
            }else{
                columnEntity.setDataType(StringUtils.swapCase(column.get("dataType")));
            }

            columnEntity.setComments(column.get("columnComment"));
            columnEntity.setExtra(column.get("extra"));
            columnEntity.setIsNullAble(column.get("isNullAble"));
            //列名转换成Java属性名
            String attrName = columnToJava(columnEntity.getColumnName());
            columnEntity.setAttrName(attrName);
            columnEntity.setAttrname(StringUtils.uncapitalize(attrName));

            //列的数据类型，转换成Java类型
            String attrType = config.getString(columnEntity.getDataType(), "unknowType");
            columnEntity.setAttrType(attrType);

            //是否主键
            if ("id".equalsIgnoreCase(column.get("columnKey")) && tableEntity.getPk() == null) {
                tableEntity.setPk(columnEntity);
            }
            allColumnName+=columnEntity.getColumnName()+", ";
            switch (columnEntity.getAttrname()){
                case "gmtCreate":
                    allColumnValue +="NOW(), ";
                    allColumnValueList +="NOW(), ";
                    break;
                case "gmtModified":
                    allColumnValue +="NOW(), ";
                    allColumnValueList +="NOW(), ";
                    break;
                case "isDelete":
                    allColumnValue +="0, ";
                    allColumnValueList +="0, ";
                    break;
                default:
                    allColumnValue +="#{"+columnEntity.getAttrname()+"}, ";
                    allColumnValueList +="#{"+tableEntity.getClassname()+"DO."+columnEntity.getAttrname()+"}, ";
                    break;
            }

            columsList.add(columnEntity);
        }
        tableEntity.setColumns(columsList);

        //没主键，则第一个字段为主键
        if (tableEntity.getPk() == null) {
            tableEntity.setPk(tableEntity.getColumns().get(0));
        }

        //设置velocity资源加载器
        Properties prop = new Properties();
        prop.put("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        Velocity.init(prop);

        //封装模板数据
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", tableEntity.getTableName());
        map.put("comments", tableEntity.getComments());
        map.put("pk", tableEntity.getPk());
        map.put("className", tableEntity.getClassName());
        map.put("classname", tableEntity.getClassname());
        map.put("pathName", tableEntity.getClassname().toLowerCase());
        map.put("columns", tableEntity.getColumns());
        map.put("package", config.getString("package"));
        map.put("author", config.getString("author"));
        map.put("email", config.getString("email"));
        map.put("datetime", DateUtils.format(new Date(), DateUtils.DATE_TIME_PATTERN));
        map.put("moduleName", config.getString("mainModule"));
        map.put("secondModuleName", toLowerCaseFirstOne(className));
        map.put("allColumnName",allColumnName.substring(0,allColumnName.length() - 2));
        map.put("allColumnValue",allColumnValue.substring(0,allColumnValue.length() - 2));
        map.put("allColumnValueList",allColumnValueList.substring(0,allColumnValue.length() - 2));
        map.put("classQueryName",classQueryName);
        VelocityContext context = new VelocityContext(map);

        //获取模板列表
        List<String> templates = getTemplates();
        for (String template : templates) {
            //渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, "UTF-8");
            tpl.merge(context, sw);

            try {
                //添加到zip
                zip.putNextEntry(new ZipEntry(getFileName(template, tableEntity.getClassName(),tableEntity.getClassname().toLowerCase(), config.getString("package"), config.getString("mainModule"))));
                IOUtils.write(sw.toString(), zip, "UTF-8");
                IOUtils.closeQuietly(sw);
                zip.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException("渲染模板失败，表名：" + tableEntity.getTableName(), e);
            }
        }
    }


    /**
     * 列名转换成Java属性名
     */
    public static String columnToJava(String columnName) {
        return WordUtils.capitalizeFully(columnName, new char[]{'_'}).replace("_", "");
    }

    /**
     * 表名转换成Java类名
     */
    public static String tableToJava(String tableName, String tablePrefix) {
        if (StringUtils.isNotBlank(tablePrefix)) {
            tableName = tableName.replace(tablePrefix, "");
        }
        return columnToJava(tableName);
    }

    /**
     * 获取配置信息
     */
    public static Configuration getConfig() {
        try {
            return new PropertiesConfiguration("generator.properties");
        } catch (ConfigurationException e) {
            throw new RuntimeException("获取配置文件失败，", e);
        }
    }

    /**
     * 获取文件名
     */
    public static String getFileName(String template, String className,String pathName, String packageName, String moduleName) {
        String packagePath = "main" + File.separator + "java" + File.separator;
        String frontPath = "ui" + File.separator;
        if (StringUtils.isNotBlank(packageName)) {
            packagePath += packageName.replace(".", File.separator) + File.separator;
        }

        if (template.contains("index.js.vm")) {
            return frontPath + "api" + File.separator + moduleName + File.separator + toLowerCaseFirstOne(className) + File.separator + "index.js";
        }

        if (template.contains("index.vue.vm")) {
            return frontPath + "views" + File.separator + moduleName + File.separator + toLowerCaseFirstOne(className) + File.separator + "index.vue";
        }

        if (template.contains("serviceimpl.java.vm")) {
            return packagePath + "biz" + File.separator + "service" + File.separator + "impl" + File.separator + pathName + File.separator + className + "ServiceImpl.java";
        }

        if (template.contains("dao.java.vm")) {
            return packagePath + "dal" + File.separator + "dao" + File.separator + pathName + File.separator + className + "DAO.java";
        }

        if (template.contains("entity.java.vm")) {
            return packagePath + "dal" + File.separator + "entity" + File.separator + pathName + File.separator + className + "DO.java";
        }

        if (template.contains("controller.java.vm")) {
            return packagePath + "rest" + File.separator + pathName + File.separator + className + "Controller.java";
        }

        if (template.contains("mapper.xml.vm")) {
            return "main" + File.separator + "resources" + File.separator + "mapper" + File.separator + className + "Mapper.xml";
        }

        if (template.contains("query.java.vm")) {
            return packagePath + "dal" + File.separator + "query" + File.separator + pathName + File.separator + className + "Query.java";
        }

        if (template.contains("converter.java.vm")) {
            return packagePath + "biz" + File.separator + "converter" + File.separator + pathName + File.separator + className + "Converter.java";
        }

        if (template.contains("service.java.vm")) {
            return packagePath + "biz" + File.separator + "service" + File.separator + pathName + File.separator + className + "Service.java";
        }

        if (template.contains("vo.java.vm")) {
            return packagePath + "biz" + File.separator + "vos" + File.separator + pathName + File.separator + className + "VO.java";
        }

        return null;
    }

    //首字母转小写
    public static String toLowerCaseFirstOne(String s) {
        if (Character.isLowerCase(s.charAt(0))) {
            return s;
        } else {
            return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
        }
    }
}
