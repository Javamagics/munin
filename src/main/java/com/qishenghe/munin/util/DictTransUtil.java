package com.qishenghe.munin.util;

import com.qishenghe.munin.cache.pack.DictEntity;
import com.qishenghe.munin.session.MuninSession;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 字典数据转换工具
 *
 * @author qishenghe
 * @date 2021/6/5 18:15
 * @change 2021/6/5 18:15 by qishenghe for init
 */
@Data
public class DictTransUtil {

    /**
     * MuninSession
     */
    private transient MuninSession muninSession;

    /**
     * 构造
     *
     * @param muninSession muninSession
     */
    public DictTransUtil(MuninSession muninSession) {
        this.muninSession = muninSession;
    }

    /**
     * 编码根据字典向原值转换
     *
     * @param result    结果
     * @param dictPoint 字典指向（字典指向优先级大于属性注解）
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    private <T> void transSingleResultCodeToMeaning(T result, Map<String, String> dictPoint) {

        // 判空
        if (result == null) {
            return;
        }
        if (dictPoint == null) {
            dictPoint = new HashMap<>(0);
        }

        // 获取类属性，转Map（key：属性名，value：属性对象）
//        Map<String, Field> fieldMap = getObjectFieldMap(result);
        // 获取类属性，转Map（key：类路径|属性名，value：属性对象）
        Map<String, Field> fieldMap = getAllFieldMap(result.getClass(), -1);

        for (String fieldKey : fieldMap.keySet()) {
            // 当前属性
            Field field = fieldMap.get(fieldKey);
            // 字典编码
            String dictCode = null;
            if (dictPoint.containsKey(field.getName())) {
                // 指向map中存在该属性
                dictCode = dictPoint.get(field.getName());
            } else {
                // 指向map中不存在
                if (field.isAnnotationPresent(MuninPoint.class)) {
                    // 被字典指向注解所修饰
                    if (!StringUtils.isEmpty(field.getAnnotation(MuninPoint.class).dictCode())) {
                        dictCode = field.getAnnotation(MuninPoint.class).dictCode();
                    }
                }
            }

            try {
                if (dictCode != null) {
                    // 需要进行转换
                    field.setAccessible(true);
                    String code = field.get(result) == null ? null : field.get(result).toString();
                    if (code != null) {
                        // 获取meaning
                        String meaning = getMeaningByCode(dictCode, code);
                        String beforeTransSplitSymbol = field.getAnnotation(MuninPoint.class).beforeTransSplitSymbol();
                        if (StringUtils.isNotEmpty(beforeTransSplitSymbol)) {
                                //表明有字段值为1，2，3，4
                                String symbol = field.getAnnotation(MuninPoint.class).beforeTransSplitSymbol();
                                String[] split = code.split(symbol);
                                List<String> meaningListTemp = new ArrayList<>();
                                // 获取meaning
                                if (split != null && split.length != 0) {
                                    for (int i = 0; i < split.length; i++) {
                                        String meaningByCode = getMeaningByCode(dictCode, split[i]);
                                        meaningListTemp.add(meaningByCode);
                                    }
                                    String afterTransSplitSymbol = field.getAnnotation(MuninPoint.class).afterTransSplitSymbol();
                                    meaning  = String.join(afterTransSplitSymbol, meaningListTemp);
                                }
                        }
                        // 执行转换
                        if (field.isAnnotationPresent(MuninPoint.class)) {
                            // 存在指向注解，根据注解中的指示进行转换覆盖

                            // 转换前code覆盖指向，转换后meaning覆盖指向
                            String beforeTransCopyTo = field.getAnnotation(MuninPoint.class).beforeTransCopyTo();
                            String overTransCopyTo = field.getAnnotation(MuninPoint.class).overTransCopyTo();

                            if (StringUtils.isEmpty(beforeTransCopyTo) && StringUtils.isEmpty(overTransCopyTo)) {
                                // 转换前指向与转换后指向字段均为空，则直接覆盖原值
                                field.set(result, meaning);
                            } else {
                                // 转换前覆盖指向空值修正（修改覆盖指向为当前字段）
                                if (StringUtils.isEmpty(beforeTransCopyTo)) {
                                    beforeTransCopyTo = field.getName();
                                }
                                // 转换后覆盖指向空值修正（修改覆盖指向为当前字段）
                                if (StringUtils.isEmpty(overTransCopyTo)) {
                                    overTransCopyTo = field.getName();
                                }
                                // 转换后Meaning保留优先级高于原值，所以先赋值转换前Code，后赋值转换后Meaning，防止转换后结果被Code覆盖
                                Field beforeTransCopyToField = fieldMap.get(field.getDeclaringClass().getName() + "|" + beforeTransCopyTo);
                                Field overTransCopyToField = fieldMap.get(field.getDeclaringClass().getName() + "|" + overTransCopyTo);
                                // 设为可修改
                                beforeTransCopyToField.setAccessible(true);
                                overTransCopyToField.setAccessible(true);
                                // 顺序修改
                                beforeTransCopyToField.set(result, field.get(result));
                                overTransCopyToField.set(result, meaning);
                            }
                        } else {
                            // 未找到指向注解，直接覆盖原值
                            field.set(result, meaning);
                        }
                    }
                }
            } catch (Exception ignored) {
                // 转换时异常，冷处理
            }
        }
    }

    /**
     * 编码根据字典向原值转换（递归处理用户自定义类型的属性）
     *
     * @param result    结果
     * @param dictPoint 字典指向（字典指向优先级大于属性注解）
     * @author qishenghe
     * @date 12/31/21 10:12 AM
     * @change 12/31/21 10:12 AM by shenghe.qi@relxtech.com for init
     * @since 1.0.0
     */
    public <T> void transResultCodeToMeaning(T result, Map<String, String> dictPoint) {

        // 递归扫描自定义类，HashSet用于存储已经转换的类对象，当出现重复时说明对象已进行过转换，自动跳过
        transResultCodeToMeaning(result, dictPoint, new HashSet<>());

    }

    /**
     * 递归处理对象下的所有自定义类
     * TODO 需要考虑当result对象使用了lombok的data注解时，hashCode方法和toString方法环图情况下调用导致的栈溢出问题
     *
     * @param result             结果
     * @param dictPoint          字典指向（字典指向优先级大于属性注解）
     * @param overTransObjectSet 存储递归处理过程中已经转换过的对象
     * @author qishenghe
     * @date 12/31/21 6:27 PM
     * @change 12/31/21 6:27 PM by shenghe.qi@relxtech.com for init
     * @since 1.0.0
     */
    private <T> void transResultCodeToMeaning(T result, Map<String, String> dictPoint, Set<Object> overTransObjectSet) {

        transSingleResultCodeToMeaning(result, dictPoint);
        // 标记该对象已被处理
        overTransObjectSet.add(result);

        Map<String, Field> fieldMap = getObjectFieldMap(result);

        for (String fieldName : fieldMap.keySet()) {

            Field field = fieldMap.get(fieldName);

            field.setAccessible(true);

            try {
                Object obj = field.get(result);

                // 判断当前对象是否已被处理，如果已被处理说明出现环图情况，跳出后续操作
                if (!overTransObjectSet.contains(obj)) {
                    if (obj != null && judgmentCustomClass(obj.getClass())) {
                        // 该属性为用户自定义类型 且 不为空
                        transResultCodeToMeaning(obj, dictPoint, overTransObjectSet);
                    }
                }

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }

    }

    /**
     * 【封装】判断类型是否是自定义类
     *
     * @param clazz 类
     * @return 是否是自定义类（true：是，false：否）
     * @author qishenghe
     * @date 12/31/21 10:51 AM
     * @change 12/31/21 10:51 AM by shenghe.qi@relxtech.com for init
     * @since 1.0.0
     */
    private static boolean judgmentCustomClass(Class<?> clazz) {
        return clazz != null && clazz.getClassLoader() != null;
    }

    /**
     * 【重载】编码根据字典向原值转换（无字典指向map，依靠注解转换）
     *
     * @param result 结果
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    public <T> void transResultCodeToMeaning(T result) {
        transResultCodeToMeaning(result, new HashMap<>(0));
    }

    /**
     * 编码根据字典向原值转换（List）
     *
     * @param resultList 结果
     * @param dictPoint  字典指向（字典指向优先级大于属性注解）
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    public <T> void transResultCodeToMeaning(List<T> resultList, Map<String, String> dictPoint) {
        for (T singleResult : resultList) {
            transResultCodeToMeaning(singleResult, dictPoint);
        }
    }

    /**
     * 【重载】编码根据字典向原值转换（List）（无字典指向map，依靠注解转换）
     *
     * @param resultList 结果
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    public <T> void transResultCodeToMeaning(List<T> resultList) {
        transResultCodeToMeaning(resultList, new HashMap<>(0));
    }

    /**
     * 编码根据字典向原值转换（List）（多线程处理）
     *
     * @param resultList 结果
     * @param dictPoint  字典指向
     * @param block      阻塞（true：阻塞，false：非阻塞）
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    public <T> List<Future> transResultCodeToMeaningMultiThread(List<T> resultList, Map<String, String> dictPoint,
                                                                boolean block) {
        List<Future> futureList = new LinkedList<>();
        for (T single : resultList) {
            Future<Boolean> singleFuture =
                    muninSession.getMuninThreadPool().getThreadPoolCpu().submit(() -> transResultCodeToMeaning(single, dictPoint), true);
            futureList.add(singleFuture);
        }

        if (block) {
            // 在方法体内阻塞执行
            for (Future single : futureList) {
                try {
                    single.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }

        return futureList;
    }

    /**
     * 【重载】编码根据字典向原值转换（List）（多线程处理）
     *
     * @param resultList 结果
     * @param block      阻塞（true：阻塞，false：非阻塞）
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    public <T> List<Future> transResultCodeToMeaningMultiThread(List<T> resultList, boolean block) {
        return transResultCodeToMeaningMultiThread(resultList, new HashMap<>(0), block);
    }

    /**
     * 【多线程阻塞处理】编码根据字典向原值转换（List）（多线程处理）
     *
     * @param resultList 结果
     * @param dictPoint  字典指向
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    public <T> void transResultCodeToMeaningMultiThread(List<T> resultList, Map<String, String> dictPoint) {
        transResultCodeToMeaningMultiThread(resultList, dictPoint, true);
    }

    /**
     * 【多线程阻塞处理】编码根据字典向原值转换（List）（多线程处理）
     *
     * @param resultList 结果
     * @author qishenghe
     * @date 2021/6/8 10:44
     * @change 2021/6/8 10:44 by qishenghe for init
     * @since 1.0.0
     */
    public <T> void transResultCodeToMeaningMultiThread(List<T> resultList) {
        transResultCodeToMeaningMultiThread(resultList, new HashMap<>(0), true);
    }

    /**
     * 【封装】根据字典编码和编码（键）获取含义（值）
     *
     * @param dictCode 字典编码
     * @param code     编码（键）
     * @return 含义（值）
     * @author qishenghe
     * @date 2021/6/8 10:40
     * @change 2021/6/8 10:40 by qishenghe for init
     * @since 1.0.0
     */
    private String getMeaningByCode(String dictCode, String code) {
        DictEntity dictEntity = muninSession.getDictCtrlUtil().getDictInfoByCode(dictCode, code);
        return dictEntity.getMeaning();
    }

    /**
     * 【封装】将对象的属性置入Map中，避免遍历
     *
     * @param object obj
     * @return Map(Obj Name - Obj)
     */
    private static Map<String, Field> getObjectFieldMap(Object object) {
        // 获取所有属性
        Field[] fields = object.getClass().getDeclaredFields();
        if (fields != null && fields.length != 0) {
            return Arrays.stream(fields).collect(Collectors.toMap(Field::getName, t -> t));
        } else {
            return new HashMap<>(0);
        }
    }

    /**
     * 【封装】获取类属性
     *
     * @param clazz    clazz
     * @param levelNum 向上（父类）搜索层数（注：-1表示无上限搜索模式）
     * @return 类属性集合（List）
     * @author qishenghe
     * @date 3/2/22 4:10 PM
     * @change 3/2/22 4:10 PM by shenghe.qi@relxtech.com for init
     * @since 1.0.0
     */
    private static List<Field> getAllFieldList(Class clazz, int levelNum) {

        Field[] declaredFields = clazz.getDeclaredFields();

        List<Field> resultList = new ArrayList<>(Arrays.asList(declaredFields));

        Class superClass = clazz.getSuperclass();
        if (superClass != null) {

            if (levelNum > 0 || levelNum == -1) {
                List<Field> fieldList = getAllFieldList(superClass, levelNum - 1);
                resultList.addAll(fieldList);
            }

        }
        return resultList;
    }

    /**
     * 【封装】获取类属性
     *
     * @param clazz    clazz
     * @param levelNum 向上（父类）搜索层数（注：-1表示无上限搜索模式）
     * @return 类属性集合（Map：key：类路径|属性名）
     * @author qishenghe
     * @date 3/2/22 4:14 PM
     * @change 3/2/22 4:14 PM by shenghe.qi@relxtech.com for init
     * @since 1.0.0
     */
    private static Map<String, Field> getAllFieldMap(Class clazz, int levelNum) {

        List<Field> allFieldList = getAllFieldList(clazz, levelNum);

        Map<String, Field> resultMap = new HashMap<>();

        for (Field singleField : allFieldList) {
            String key = singleField.getDeclaringClass().getName() + "|" + singleField.getName();
            resultMap.put(key, singleField);
        }
        return resultMap;
    }

}
