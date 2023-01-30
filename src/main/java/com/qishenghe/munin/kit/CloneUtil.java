package com.qishenghe.munin.kit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * 拷贝工具
 *
 * @author qishenghe
 * @date 2021/6/7 21:20
 * @change 2021/6/7 21:20 by qishenghe for init
 */
public class CloneUtil {

    /**
     * 深拷贝
     *
     * @param src 拷贝源
     * @return 拷贝副本
     * @since 1.0.0
     * @author qishenghe
     * @date 2021/6/8 9:38
     * @change 2021/6/8 9:38 by qishenghe for init
     */
    public static <T> T deepCopy(T src) {
        /**
         * q1:为什么要使用拷贝
         * a1:在多线程的情况下，如果有一个对象被两个线程同时调用，则后面进来的线程对这个对象的修改会影响到其他线程
         * q2:深拷贝和浅拷贝的区别
         * a2:主要区别为，对某一个对象中的引用类型的属性是否共享（浅拷贝：共享，深拷贝：不会共享）
         * 如果某个对象中age的类型为Integer，这时用浅拷贝会发现，age属性会被多个线程共享，即，会出现a1的情况
         * 齐哥这种深拷贝方法是一种通过jdk序列化实现的，还可以通过json来进行深拷贝
         * g1:个人理解，深拷贝，其实就是new一个对象,只不过new的时候这个对象是有初值的
         * q3:这里为什么要用深拷贝
         * g2:查看调用这个工具类的地方可以发现，如果是只读的，返回的是对象本身，但如果不是只读，则进行了一个深拷贝。
         * 是为了避免出现a1问题
         *
         */
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(byteOut);
            out.writeObject(src);

            ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
            ObjectInputStream in = new ObjectInputStream(byteIn);
            @SuppressWarnings("unchecked")
            T dest = (T) in.readObject();
            return dest;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
