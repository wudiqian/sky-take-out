package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turnoverList=new ArrayList<>();

        for (LocalDate date : dateList) {
            //select sum(amount) from orders where order_time> ? and order_time< ? and status=5
            LocalDateTime beginTime=LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(date, LocalTime.MAX);
            Map map=new HashMap<>();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);

            Double turnover=orderMapper.sumByMap(map);

            if(turnover==null){
                turnover=0.0;
            }
            turnoverList.add(turnover);
            
        }

        return TurnoverReportVO.builder().
                dateList(StringUtils.join(dateList,",")).
                turnoverList(StringUtils.join(turnoverList,",")).
                build();
    }



    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Integer> newUserList=new ArrayList<>();

        List<Integer> totalUserList=new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime= LocalDateTime.of(date,LocalTime.MIN);
            LocalDateTime endTime= LocalDateTime.of(date,LocalTime.MAX);
            Map map=new HashMap();
            map.put("end",endTime);
            Integer totalUser=userMapper.CountByMap(map);
            map.put("begin",beginTime);
            Integer newUser=userMapper.CountByMap(map);
            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }
        //创建时间判断

        return UserReportVO.builder().dateList(StringUtils.join(dateList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .build();
    }

    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {

        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        List<Integer> orderCountList=new ArrayList<>();
        List<Integer> validOrderCountList=new ArrayList<>();
        //查询每天的有效订单数和订单总数
        for (LocalDate date : dateList) {
            //select count(id) from orders where order_time>? and order_time<? and status=5
            LocalDateTime beginTime= LocalDateTime.of(date,LocalTime.MIN);
            LocalDateTime endTime= LocalDateTime.of(date,LocalTime.MAX);
            Integer orderCount=getOrderCount(beginTime,endTime,null);
            Integer validOrderCount =getOrderCount(beginTime,endTime,Orders.COMPLETED);
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }
        Integer totalOrderCount=orderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount=validOrderCountList.stream().reduce(Integer::sum).get();

        Double orderCompletionRate= 0.0;
        if(totalOrderCount!=0) {
            orderCompletionRate= validOrderCount.doubleValue() / totalOrderCount.doubleValue();
        }
        Integer sumOrders;



        return OrderReportVO.builder().dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .orderCompletionRate(orderCompletionRate)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .build();
    }

    private  Integer getOrderCount(LocalDateTime begin,LocalDateTime end,Integer status){
        Map map=new HashMap();
        map.put("end",end);
        Integer totalUser=userMapper.CountByMap(map);
        map.put("begin",begin);
        Integer newUser=userMapper.CountByMap(map);
        map.put("status",status);
        return orderMapper.countByMap(map);
    }



    @Override
    public SalesTop10ReportVO getSaleTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(beginTime, endTime);


        String nameList = StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()),",");
        String numberList = StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList()),",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    @Override
    public void exportBussinessData(HttpServletResponse response) {
        //查询数据库
        LocalDate dateBegin=LocalDate.now().minusDays(30);
        LocalDate dateEnd=LocalDate.now().minusDays(1);

        BusinessDataVO businessDataVO=workspaceService.getBusinessData( LocalDateTime.of(dateBegin,LocalTime.MIN),LocalDateTime.of(dateEnd,LocalTime.MAX));


        //写入excel
        InputStream in=this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            XSSFWorkbook excel=new XSSFWorkbook(in);
            XSSFSheet sheet= excel.getSheet("Sheet1");
            sheet.getRow(1).getCell(1).setCellValue("时间："+dateBegin+"至"+dateEnd);
            XSSFRow row=sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());

            row=sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            //明细数据
            for(int i=0;i<30;i++){
                LocalDate date=dateBegin.plusDays(i);
                BusinessDataVO businessData=workspaceService.getBusinessData(LocalDateTime.of(date,LocalTime.MIN),LocalDateTime.of(date,LocalTime.MAX));
                row=sheet.getRow(i+7);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }



            ServletOutputStream out= response.getOutputStream();
            excel.write(out);
            out.close();
            excel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        //下载到客户端浏览器


    }


}
