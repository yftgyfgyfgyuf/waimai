package com.itheima.reggie.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.reggie.entity.Employee;
import com.itheima.reggie.entity.OrderDetail;

/*
    mybatisplus管得很宽，明明是持久层框架，但是他把service也给管了
    当前的service是操作employee模块的service
 */
public interface OrderDetailService extends IService<OrderDetail> {

}
