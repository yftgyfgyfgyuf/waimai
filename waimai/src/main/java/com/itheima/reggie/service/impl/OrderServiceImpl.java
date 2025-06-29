package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.BaseContext;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.dto.OrdersDto;
import com.itheima.reggie.entity.*;
import com.itheima.reggie.mapper.OrderMapper;
import com.itheima.reggie.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/*
    这个是一个Employee模块的service实现类，这个类可以调用EmployeeMapper 完成对数据库增删改查
 */
@Service//在服务器启动的时候，就创建service对象，并且放入容器中，其他地方要使用service只需要从容器中注入即可
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService {
    @Autowired
    private ShoppingCartService shoppingCartService;
    @Autowired
    private AddressBookService addressBookService;
    @Autowired
    private UserService userService;
    @Autowired
    private OrderDetailService orderDetailService;
    /*
        如何生成订单
        如何生成订单明细集合
        0.查询购物车
        1.保存订单表
        2.基于订单id，保存订单明细表
        3.清空购物车
     */
    @Override
    @Transactional
    public void submit(Orders orders) {
        /*
            1.根据当前的userId查询当前用户的购物车
         */
        LambdaQueryWrapper<ShoppingCart> shoppingCartQueryWrapper = new LambdaQueryWrapper<>();
        shoppingCartQueryWrapper.eq(ShoppingCart::getUserId,BaseContext.getCurrentId());
        List<ShoppingCart> list = shoppingCartService.list(shoppingCartQueryWrapper);
        if(list==null||list.size()<=0){
            throw new CustomException("购物车中无数据，无法生成订单");
        }
        /*
            2.完善订单属性
         */
        /*
            IdWorker.getIdStr() 雪花算法生成一个唯一的字符串
         */
        orders.setNumber(IdWorker.getIdStr());//订单编号
        orders.setStatus(2);//默认支付成功
        orders.setUserId(BaseContext.getCurrentId());//用户id
        //计算当前购物车中的这些菜品，总价一共是多少？
        BigDecimal amount = new BigDecimal("0");//准备了一个变量来存放金额
        for (ShoppingCart cart : list) {
            //将每个购物项的金额加起来。 number * 单价
            BigDecimal num = new BigDecimal(cart.getNumber());//数量
            BigDecimal cartAmount = cart.getAmount();//单价
            BigDecimal multiply= num.multiply(cartAmount);//数量* 单价 = 当前购物项总价

            //记录当前购物项的总价
            amount = amount.add(multiply);
        }
        orders.setAmount(amount);//设置订单的总价
        //设置和地址相关的几个参数
        AddressBook addressBook = addressBookService.getById(orders.getAddressBookId());
        if(addressBook!=null){
            orders.setPhone(addressBook.getPhone());//收件人的手机号
            orders.setAddress(addressBook.getDetail());//设置地址详情
            orders.setConsignee(addressBook.getConsignee());//设置收件人信息
        }
        //设置订单中下单用户名
        User user = userService.getById(BaseContext.getCurrentId());
        if(user!=null){
            orders.setUserName(user.getName());
        }
        orders.setOrderTime(LocalDateTime.now());
        orders.setCheckoutTime(LocalDateTime.now());//付款时间，我们当前系统没有做支付的，下单即付款。把支付跳过了
        //保存订单
        super.save(orders);//保存订单，雪花算法就会生成订单的id
        /*
            3.保存订单详情
            因为购物项 和 订单几乎是属性相同的，所以我们直接完成购物车到订单属性拷贝
         */
        List<OrderDetail> detailList = list.stream().map(shoppingCart -> {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart, orderDetail, "id");//除id以外，其他属性能对上就拷贝
            //手动设置好订单的id
            orderDetail.setOrderId(orders.getId());//订单详情对象 与订单建立关系
            return orderDetail;
        }).collect(Collectors.toList());
        //批量保存订单详情
        orderDetailService.saveBatch(detailList);

        /*
            4.清空购物车
         */
        LambdaUpdateWrapper<ShoppingCart> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ShoppingCart::getUserId,BaseContext.getCurrentId());
        //delete from shoppingCart where user_id = ?
        shoppingCartService.remove(updateWrapper);
    }
    /*
        查询订单以及订单详情集合，把所有数据都封装进 pageInfo<OrdersDto> 容器
     */
    @Override
    public Page<OrdersDto> pageOrdersAndOrdersDetail(Integer page, Integer pageSize) {
        //1.查询订单
        Page<Orders> pageInfo = new Page<>(page,pageSize);
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(Orders::getCheckoutTime);
        //只能查自己的订单
        queryWrapper.eq(Orders::getUserId, BaseContext.getCurrentId());
        super.page(pageInfo,queryWrapper);//查询结果会自动封装到pageInfo内部
        //2.把订单分页数据转成订单的Dto分页数据
        Page<OrdersDto> ordersDtoPage = new Page<OrdersDto>();
        BeanUtils.copyProperties(pageInfo,ordersDtoPage,"records");
        //3.完成订单详情的集合封装
        List<OrdersDto> ordersDtos = pageInfo.getRecords().stream().map(orders -> {
            OrdersDto ordersDto = new OrdersDto();
            //订单转dto
            BeanUtils.copyProperties(orders, ordersDto);
            //订单详情集合
            LambdaQueryWrapper<OrderDetail> orderDetailLambdaQueryWrapper = new LambdaQueryWrapper<>();
            orderDetailLambdaQueryWrapper.eq(OrderDetail::getOrderId, orders.getId());
            List<OrderDetail> ordersDetailList = orderDetailService.list(orderDetailLambdaQueryWrapper);//select * from ordersDetails where order_Id=?
            ordersDto.setOrderDetails(ordersDetailList);
            return ordersDto;
        }).collect(Collectors.toList());
        //4.手动封装dto分页中的records属性
        ordersDtoPage.setRecords(ordersDtos);
        return ordersDtoPage;
    }
}
