package org.voovan.tools.json;

import org.voovan.Global;
import org.voovan.tools.TDateTime;
import org.voovan.tools.TString;
import org.voovan.tools.reflect.TReflect;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JSON打包类
 *
 * @author helyho
 * <p>
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class JSONEncode {

    /**
     * 分析自定义对象为JSON字符串
     *
     * @param object 自定义对象
     * @return JSON字符串
     * @throws ReflectiveOperationException
     */
    private static String complexObject(Object object, boolean allField) throws ReflectiveOperationException {
        return mapObject(TReflect.getMapfromObject(object, allField), allField);
    }

    /**
     * 分析Map对象为JSON字符串
     *
     * @param mapObject map对象b
     * @return JSON字符串
     * @throws ReflectiveOperationException
     */
    private static String mapObject(Map<?, ?> mapObject, boolean allField) throws ReflectiveOperationException {
        StringBuilder contentStringBuilder = new StringBuilder(Global.STR_LC_BRACES);

        for (Object mapkey : mapObject.keySet()) {
            String key = fromObject(mapkey, allField);
            String Value = fromObject(mapObject.get(mapkey), allField);
            String wrapQuote = key.startsWith(Global.STR_QUOTE) && key.endsWith(Global.STR_QUOTE) ? Global.EMPTY_STRING : Global.STR_QUOTE;
            contentStringBuilder.append(wrapQuote);
            contentStringBuilder.append(key);
            contentStringBuilder.append(wrapQuote);
            contentStringBuilder.append(Global.STR_COLON);
            contentStringBuilder.append(Value);
            contentStringBuilder.append(Global.STR_COMMA);
        }

        if (contentStringBuilder.length()>1){
            contentStringBuilder.setLength(contentStringBuilder.length() - 1);
        }

        contentStringBuilder.append(Global.STR_RC_BRACES);

        return contentStringBuilder.toString();
    }

    /**
     * 分析Array对象为JSON字符串
     *
     * @param arrayObject Array对象
     * @throws ReflectiveOperationException
     * @return JSON字符串
     */
    private static String arrayObject(Object[] arrayObject, boolean allField) throws ReflectiveOperationException {
        StringBuilder contentStringBuilder = new StringBuilder(Global.STR_LS_BRACES);

        for (Object object : arrayObject) {
            String Value = fromObject(object, allField);
            contentStringBuilder.append(Value);
            contentStringBuilder.append(Global.STR_COMMA);
        }

        if (contentStringBuilder.length()>1){
            contentStringBuilder.setLength(contentStringBuilder.length() - 1);
        }

        contentStringBuilder.append(Global.STR_RS_BRACES);
        return contentStringBuilder.toString();
    }

    /**
     * 分析Collection对象为JSON字符串
     *
     * @param collectionObject Collection 对象
     * @throws ReflectiveOperationException
     * @return JSON字符串
     */
    private static String CollectionObject(Collection collectionObject, boolean allField) throws ReflectiveOperationException {
        StringBuilder contentStringBuilder = new StringBuilder(Global.STR_LS_BRACES);

        for (Object object : collectionObject) {
            String Value = fromObject(object, allField);
            contentStringBuilder.append(Value);
            contentStringBuilder.append(Global.STR_COMMA);
        }

        if (contentStringBuilder.length()>1){
            contentStringBuilder.setLength(contentStringBuilder.length() - 1);
        }

        contentStringBuilder.append(Global.STR_RS_BRACES);
        return contentStringBuilder.toString();
    }

    /**
     * 将对象转换成JSON字符串
     *
     * @param object 要转换的对象
     * @return 类型:String 		对象对应的JSON字符串
     * @throws ReflectiveOperationException 反射异常
     */
    @SuppressWarnings("unchecked")
    public static String fromObject(Object object) throws ReflectiveOperationException {
        return fromObject(object, false);
    }

    /**
     * 将对象转换成JSON字符串
     *
     * @param object 要转换的对象
     * @param allField 是否处理所有属性
     * @return 类型:String 		对象对应的JSON字符串
     * @throws ReflectiveOperationException 反射异常
     */
    @SuppressWarnings("unchecked")
    public static String fromObject(Object object, boolean allField) throws ReflectiveOperationException {
        String value = null;

        if (object == null) {
            return "null";
        }

        Class clazz = object.getClass();

        if (object instanceof Class) {
            return "\"" + ((Class)object).getCanonicalName() + "\"";
        } else if (object instanceof BigDecimal) {
            if(BigDecimal.ZERO.compareTo((BigDecimal)object)==0){
                object = BigDecimal.ZERO;
            }

            value = ((BigDecimal) object).toPlainString();
        } else if (object instanceof Date) {
            value = "\"" + TDateTime.format(((Date)object), TDateTime.STANDER_DATETIME_TEMPLATE)+ "\"";;
        } else if (object instanceof Map) {
            Map<Object, Object> mapObject = (Map<Object, Object>) object;
            value = mapObject(mapObject, allField);
        } else if (object instanceof Collection) {
            Collection<Object> collectionObject = (Collection<Object>)object;
            value = CollectionObject(collectionObject, allField);
        } else if (clazz.isArray()) {
            Object[] arrayObject = (Object[])object;
            //如果是 java 基本类型, 则转换成对象数组
            if(clazz.getComponentType().isPrimitive()) {
                int length = Array.getLength(object);
                arrayObject = new Object[length];
                for(int i=0;i<length;i++){
                    arrayObject[i] = Array.get(object, i);
                }
            } else {
                arrayObject = (Object[])object;
            }
            value = arrayObject(arrayObject, allField);
        } else if (object instanceof Integer ||  object instanceof Float ||
                object instanceof Double || object instanceof Boolean ||
                object instanceof Long || object instanceof Short) {
            value = object.toString();
        } else if (TReflect.isBasicType(object.getClass())) {
            //这里这么做的目的是方便 js 中通过 eval 方法产生 js 对象
            String strValue = object.toString();
            if(JSON.isConvertEscapeChar()) {
                strValue = TString.convertEscapeChar(object.toString());
            }
            value = "\"" + strValue + "\"";
        }  else {
            value = complexObject(object, allField);
        }

        return value;
    }


}
