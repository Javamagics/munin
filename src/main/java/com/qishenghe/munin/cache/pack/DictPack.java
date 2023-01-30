package com.qishenghe.munin.cache.pack;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * 字典缓存容器
 *
 * @author qishenghe
 * @date 2021/6/5 18:09
 * @change 2021/6/5 18:09 by qishenghe for init
 * startTime 22:10
 * endTime 23:10
 * majorProblem:HashMap
 * getResult : 猜测字典在缓存中的形式为<字典编码,<code,meaning>> dui
 */
@Data
public class DictPack implements Serializable {

    /**
     * 读写分离
     */
    ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();

    /**
     * 写
     */
    WriteLock writeLock = reentrantReadWriteLock.writeLock();

    /**
     * 读
     */
    ReadLock readLock = reentrantReadWriteLock.readLock();

    /**
     * 缓存容器
     */
    private Map<String, DictSinglePack> dictPack;

    /**
     * 合并缓存容器【生成合并后的副本并返回】
     *
     * @param packs 需要合并的各个子容器（注意传入顺序，表示合并优先级，前置容器优先级大）
     * @return 合并后的容器 （注：合并优先级，当Key值出现冲突，优先级大的覆盖优先级小的）
     * @since 1.0.0
     * @author qishenghe
     * @date 2021/6/7 10:28
     * @change 2021/6/7 10:28 by qishenghe for init
     */
    public static DictPack merge(DictPack... packs) {
        // 降低加载因子，减少Hash冲突可能性，提升查询效率
        /**
         * 加载因子：用来衡量HashMap中元素满的程度
         * 加载因子 = 填入表中的元素个数 / 散列表的长度
         *
         * 加载因子越大，填满的元素越多，空间利用率越高，但发生冲突的机会变大了；
         *
         * 加载因子越小，填满的元素越少，冲突发生的机会减小，但空间浪费了更多了，而且还会提高扩容rehash操作的次数。
         * q1:为什么加载因子降成了0.5
         * g1:计算HashMap的实时加载因子的方法为：size/capacity,按照这个公式的话，size为500,但是这又引发了一个新的问题
         * HashMap的size指的是该HashMap中的键值对总数，在编写代码时这个值不能确定
         * g2:初始容量为100，加载因子为0.5，即数组中如果有50个元素，就进行扩容，rehash一次。数组填满的元素较少，造成hash冲突的可能性就
         * 会降低，从而降低元素跑到链表（查询速度慢）里面。从而提高查询效率，但还是不理解为什么这个值是0.5
         * 如果是为了提高速度，可以拿空间换时间。直接把这个初始容量指定为一个比较大的值，例如1000，加载因子为0.05
         * e1:（wxs想办法实现这个初始容量和加载因子可以由用户指定），想法：猜可以定义一个类，用户可以选择写一个配置类继承这个类中的方法
         * 例如提供一个get 一个set，用户set了以后，我在这个类中get。
         */
        // 容器
        Map<String, DictSinglePack> tmpDictPack = new HashMap<>(100, 0.5f);

        // 优先级自低向高遍历，后入覆盖
        if (packs != null) {
            for (int i = packs.length - 1; i >= 0; i--) {
                DictPack singlePack = packs[i];
                tmpDictPack.putAll(singlePack.getDictPack());
            }
        } else {
            tmpDictPack = new HashMap<>(0);
        }

        // 生成容器副本
        /**
         * q1:为啥要生成容器副本？
         * g1：备份嘛？
         */
        DictPack result = new DictPack();
        result.setDictPack(tmpDictPack);

        return result;
    }

    /**
     * 根据接入的源数据生成字典容器
     *
     * @param initData 输入源数据
     * @return 字典容器
     * @since 1.0.0
     * @author qishenghe
     * @date 2021/6/7 14:17
     * @change 2021/6/7 14:17 by qishenghe for init
     */
    public static DictPack createDictPackByInitData(List<DictEntity> initData) {
        DictPack result = new DictPack();
        if (initData == null || initData.size() == 0) {
            // 空
            result.setDictPack(new HashMap<>(0));
        } else {
            // 初始化数据预处理（sortNum空值处理，sortNum为空时赋值-1）
            setSortNumDefaultValueIfNull(initData);
            // 分组
            /**
             * g1:分组的目的是为了加快后期字典转码的速度，以字典编码为key，<code,meaning>为value，
             * 这样的话在进行字典转码的时候，可以通过key值快速定位这个字典编码下的code以及meaning。不然的话需要在缓存中
             * 以code为key进行全部查询，降低了查询速度，并且以code为key的话会冲突 对
             */
            Map<String, List<DictEntity>> groupMap = new HashMap<>(initData.size());
            // 执行分组
            for (DictEntity single : initData) {
                // 字典编码
                String dictCode = single.getDictCode();
                if (!StringUtils.isEmpty(dictCode)) {
                    if (groupMap.containsKey(dictCode)) {
                        /**
                         * 如果已有这个分组了，就把这个分组的数据拿出来，再把新的code，meaning放进去
                         * 然后更新这个分组
                         */
                        List<DictEntity> tmpList = groupMap.get(dictCode);
                        tmpList.add(single);
                        groupMap.put(dictCode, tmpList);
                    } else {
                        /**
                         * q1:这里采用了LinkedList，可是这个东西是插入和删除速度快，如果是为了加快查询速度的话，应该用ArrayList
                         * g1:盲猜是为了补偿一点插入速度嘛？毕竟项目启动时，插入动作的速度也十分关键
                         *
                         */
                        List<DictEntity> tmpList = new LinkedList<>();
                        tmpList.add(single);
                        groupMap.put(dictCode, tmpList);
                    }
                }
            }
            // 处理分组数据生成字典容器
            /**
             * g1:groupMap.size() << 1相当于groupMap.size() *2，但是这里的加载因子又是0.5
             * 结合上面的猜测，这里的<<1可能是为了让这个size成为2的倍数，以此来让数据填满到数组大小的一半时再进行扩容
             */
            Map<String, DictSinglePack> resultDictPack = new HashMap<>(groupMap.size() << 1, 0.5f);
            for (String dictCode : groupMap.keySet()) {
                DictSinglePack singleDict = DictSinglePack.createSinglePackByTargetDictData(groupMap.get(dictCode));
                resultDictPack.put(dictCode, singleDict);
            }
            // 赋值
            result.setDictPack(resultDictPack);
        }
        return result;
    }

    /**
     * 【封装】初始化数据预处理（sortNum空值处理，sortNum为空时赋值-1）
     *
     * @param initData 初始化数据集
     * @since 1.0.0
     * @author qishenghe
     * @date 2021/6/8 13:40
     * @change 2021/6/8 13:40 by qishenghe for init
     */
    private static void setSortNumDefaultValueIfNull(List<DictEntity> initData) {
        for (DictEntity single : initData) {
            if (single != null && single.getSortNum() == null) {
                single.setSortNum(-1);
            }
        }
    }

}
