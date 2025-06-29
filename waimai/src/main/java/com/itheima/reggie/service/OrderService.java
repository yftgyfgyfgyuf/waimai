package com.itheima.reggie.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.reggie.dto.OrdersDto;
import com.itheima.reggie.entity.Orders;

/*
    mybatisplus管得很宽，明明是持久层框架，但是他把service也给管了
 */
public interface OrderService extends IService<Orders> {

    void submit(Orders orders);

    Page<OrdersDto> pageOrdersAndOrdersDetail(Integer page, Integer pageSize);

}
