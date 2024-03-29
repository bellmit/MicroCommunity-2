package com.java110.report.smo.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.java110.core.factory.GenerateCodeFactory;
import com.java110.core.smo.IComputeFeeSMO;
import com.java110.dto.RoomDto;
import com.java110.dto.fee.FeeDto;
import com.java110.dto.owner.OwnerCarDto;
import com.java110.dto.report.ReportCarDto;
import com.java110.dto.report.ReportFeeDetailDto;
import com.java110.dto.report.ReportFeeDto;
import com.java110.dto.report.ReportRoomDto;
import com.java110.dto.reportFeeMonthStatistics.ReportFeeMonthStatisticsDto;
import com.java110.intf.report.IGeneratorFeeMonthStatisticsInnerServiceSMO;
import com.java110.intf.user.IOwnerCarInnerServiceSMO;
import com.java110.po.reportFeeMonthStatistics.ReportFeeMonthStatisticsPo;
import com.java110.report.dao.IReportCommunityServiceDao;
import com.java110.report.dao.IReportFeeMonthStatisticsServiceDao;
import com.java110.report.dao.IReportFeeServiceDao;
import com.java110.utils.util.Assert;
import com.java110.utils.util.BeanConvertUtil;
import com.java110.utils.util.DateUtil;
import com.java110.utils.util.ListUtil;
import com.java110.utils.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName GeneratorFeeMonthStatisticsInnerServiceSMOImpl
 * @Description TODO
 * @Author wuxw
 * @Date 2020/10/15 21:53
 * @Version 1.0
 * add by wuxw 2020/10/15
 **/
@RestController
public class GeneratorFeeMonthStatisticsInnerServiceSMOImpl implements IGeneratorFeeMonthStatisticsInnerServiceSMO {
    private static final Logger logger = LoggerFactory.getLogger(GeneratorFeeMonthStatisticsInnerServiceSMOImpl.class);

    //默认 处理房屋数量
    private static final int DEFAULT_DEAL_ROOM_COUNT = 1000;

    @Autowired
    private IReportFeeMonthStatisticsServiceDao reportFeeMonthStatisticsServiceDaoImpl;

    @Autowired
    private IReportCommunityServiceDao reportCommunityServiceDaoImpl;

    @Autowired
    private IReportFeeServiceDao reportFeeServiceDaoImpl;

    @Autowired
    private IComputeFeeSMO computeFeeSMOImpl;

    @Autowired
    private IOwnerCarInnerServiceSMO ownerCarInnerServiceSMOImpl;

    @Override
    public int generatorData(@RequestBody ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo) {

        doGeneratorData(reportFeeMonthStatisticsPo);
        return 0;
    }

    @Async
    private void doGeneratorData(ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo) {
        String communityId = reportFeeMonthStatisticsPo.getCommunityId();

        Assert.hasLength(communityId, "未包含小区信息");

        //处理房屋费用
        dealRoomFee(reportFeeMonthStatisticsPo);

        //处理车位费用
        dealCarFee(reportFeeMonthStatisticsPo);

        //处理缴费结束的情况
        dealFinishFee(communityId);
    }

    private void dealFinishFee(String communityId) {
        Map reportFeeDto = new HashMap();
        reportFeeDto.put("communityId", communityId);
        List<Map> feeDtos = reportFeeMonthStatisticsServiceDaoImpl.queryFinishOweFee(reportFeeDto);
        ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo = null;
        for (Map info : feeDtos) {
            try {
                Date tmpTime = DateUtil.getDateFromString(info.get("feeYear").toString() + "-" + info.get("feeMonth").toString() + "-01", DateUtil.DATE_FORMATE_STRING_B);
                Calendar c = Calendar.getInstance();
                c.setTime(tmpTime);
                c.add(Calendar.MONTH, 1);
                ReportFeeDetailDto feeDetailDto = new ReportFeeDetailDto();
                feeDetailDto.setStartTime(DateUtil.getFormatTimeString(tmpTime, DateUtil.DATE_FORMATE_STRING_A));
                feeDetailDto.setEndTime(DateUtil.getFormatTimeString(c.getTime(), DateUtil.DATE_FORMATE_STRING_A));
                feeDetailDto.setFeeId(info.get("feeId").toString());
                double receivedAmount = reportFeeServiceDaoImpl.getFeeReceivedAmount(feeDetailDto);
                reportFeeMonthStatisticsPo = new ReportFeeMonthStatisticsPo();
                reportFeeMonthStatisticsPo.setStatisticsId(info.get("statisticsId").toString());
                reportFeeMonthStatisticsPo.setReceivedAmount(receivedAmount + "");
                reportFeeMonthStatisticsPo.setOweAmount("0");
                reportFeeMonthStatisticsPo.setUpdateTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
                reportFeeMonthStatisticsServiceDaoImpl.updateReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsPo));
            } catch (Exception e) {
                logger.error("处理 缴费结束报表失败", e);
            }
        }
    }

    /**
     * 处理车位 车辆费用
     *
     * @param reportFeeMonthStatisticsPo
     */
    private void dealCarFee(ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo) {

        int page = 0;
        int max = DEFAULT_DEAL_ROOM_COUNT;

        ReportCarDto reportCarDto = new ReportCarDto();
        reportCarDto.setCommunityId(reportFeeMonthStatisticsPo.getCommunityId());
        int count = reportCommunityServiceDaoImpl.getCarCount(reportCarDto);


        if (count < DEFAULT_DEAL_ROOM_COUNT) {
            page = 1;
            max = count;
        } else {
            page = (int) Math.ceil((double) count / (double) DEFAULT_DEAL_ROOM_COUNT);
            max = DEFAULT_DEAL_ROOM_COUNT;
        }

        for (int pageIndex = 0; pageIndex < page; pageIndex++) {
            reportCarDto.setPage(pageIndex * max);
            reportCarDto.setRow(max);
            List<ReportCarDto> reportRoomDtos = reportCommunityServiceDaoImpl.getCarParkingSpace(reportCarDto);
            for (ReportCarDto tmpReportCarDto : reportRoomDtos) {
                try {
                    doDealCarFees(tmpReportCarDto);
                } catch (Exception e) {
                    logger.error("生成费用报表失败" + JSONObject.toJSONString(tmpReportCarDto), e);
                }
            }
        }
    }


    /**
     * 处理 房屋费用
     *
     * @param reportFeeMonthStatisticsPo
     */
    private void dealRoomFee(ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo) {

        int page = 0;
        int max = DEFAULT_DEAL_ROOM_COUNT;

        ReportRoomDto reportRoomDto = new ReportRoomDto();
        reportRoomDto.setCommunityId(reportFeeMonthStatisticsPo.getCommunityId());
        int count = reportCommunityServiceDaoImpl.getRoomCount(reportRoomDto);


        if (count < DEFAULT_DEAL_ROOM_COUNT) {
            page = 1;
            max = count;
        } else {
            page = (int) Math.ceil((double) count / (double) DEFAULT_DEAL_ROOM_COUNT);
            max = DEFAULT_DEAL_ROOM_COUNT;
        }

        for (int pageIndex = 0; pageIndex < page; pageIndex++) {
            reportRoomDto.setPage(pageIndex * max);
            reportRoomDto.setRow(max);
            List<ReportRoomDto> reportRoomDtos = reportCommunityServiceDaoImpl.getRoomFloorUnitAndOwner(reportRoomDto);
            for (ReportRoomDto tmpReportRoomDto : reportRoomDtos) {
                try {
                    doDealRoomFees(tmpReportRoomDto);
                } catch (Exception e) {
                    logger.error("生成费用报表失败" + JSONObject.toJSONString(tmpReportRoomDto), e);
                }
            }
        }
    }

    private void doDealCarFees(ReportCarDto tmpReportCarDto) {
        ReportFeeDto reportFeeDto = new ReportFeeDto();
        reportFeeDto.setPayerObjId(tmpReportCarDto.getCarId());
        reportFeeDto.setPayerObjType(FeeDto.PAYER_OBJ_TYPE_CAR);
        //reportFeeDto.setState(FeeDto.STATE_DOING);
        List<ReportFeeDto> feeDtos = reportFeeServiceDaoImpl.getFees(reportFeeDto);

        if (feeDtos == null || feeDtos.size() < 1) {
            return;
        }

        for (ReportFeeDto tmpReportFeeDto : feeDtos) {
            try {
                doDealCarFee(tmpReportCarDto, tmpReportFeeDto);
            } catch (Exception e) {
                logger.error("处理车辆费用失败" + JSONObject.toJSONString(tmpReportFeeDto), e);
            }
        }
    }

    private void doDealCarFee(ReportCarDto tmpReportCarDto, ReportFeeDto tmpReportFeeDto) {

        //费用已经结束 并且当月实收为0 那就是 之前就结束了 无需处理  && ListUtil.isNull(statistics)
        if (FeeDto.STATE_FINISH.equals(tmpReportFeeDto.getState())
                && getCurFeeReceivedAmount(tmpReportFeeDto) == 0) {
            return;
        }
        ReportFeeMonthStatisticsDto reportFeeMonthStatisticsDto = new ReportFeeMonthStatisticsDto();
        reportFeeMonthStatisticsDto.setCommunityId(tmpReportCarDto.getCommunityId());
        reportFeeMonthStatisticsDto.setConfigId(tmpReportFeeDto.getConfigId());
        reportFeeMonthStatisticsDto.setObjId(tmpReportFeeDto.getPayerObjId());
        //reportFeeMonthStatisticsDto.setFeeId(tmpReportFeeDto.getFeeId());
        reportFeeMonthStatisticsDto.setObjType(tmpReportFeeDto.getPayerObjType());
        reportFeeMonthStatisticsDto.setFeeYear(DateUtil.getYear() + "");
        reportFeeMonthStatisticsDto.setFeeMonth(DateUtil.getMonth() + "");
        List<ReportFeeMonthStatisticsDto> statistics = BeanConvertUtil.covertBeanList(
                reportFeeMonthStatisticsServiceDaoImpl.getReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsDto)),
                ReportFeeMonthStatisticsDto.class);

        double receivedAmount = getReceivedAmount(tmpReportFeeDto); //实收

        FeeDto feeDto = BeanConvertUtil.covertBean(tmpReportFeeDto, FeeDto.class);
        OwnerCarDto ownerCarDto = BeanConvertUtil.covertBean(tmpReportCarDto, OwnerCarDto.class);
        Map<String, Object> targetEndDateAndOweMonth = computeFeeSMOImpl.getTargetEndDateAndOweMonth(feeDto, ownerCarDto);

        Date targetEndDate = (Date) targetEndDateAndOweMonth.get("targetEndDate");
        tmpReportFeeDto.setDeadlineTime(targetEndDate);
        double oweAmount = getOweAmountByCar(tmpReportFeeDto, null, tmpReportCarDto); //应收

        dealBeforeUploadCarFee(tmpReportFeeDto, tmpReportCarDto);
        double receivableAmount = getReceivableAmount(tmpReportFeeDto,receivedAmount); //欠费


        ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo = new ReportFeeMonthStatisticsPo();
        reportFeeMonthStatisticsPo.setDeadlineTime(DateUtil.getFormatTimeString(targetEndDate, DateUtil.DATE_FORMATE_STRING_A));
        if (!ListUtil.isNull(statistics)) {
            ReportFeeMonthStatisticsDto statistic = statistics.get(0);
            reportFeeMonthStatisticsPo.setStatisticsId(statistic.getStatisticsId());
            reportFeeMonthStatisticsPo.setReceivableAmount(receivableAmount + "");
            reportFeeMonthStatisticsPo.setReceivedAmount(receivedAmount + "");
            reportFeeMonthStatisticsPo.setOweAmount(oweAmount + "");
            reportFeeMonthStatisticsPo.setUpdateTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsServiceDaoImpl.updateReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsPo));
        } else {
            reportFeeMonthStatisticsPo.setOweAmount(oweAmount + "");
            reportFeeMonthStatisticsPo.setReceivedAmount(receivedAmount + "");
            reportFeeMonthStatisticsPo.setReceivableAmount(receivableAmount + "");
            reportFeeMonthStatisticsPo.setStatisticsId(GenerateCodeFactory.getGeneratorId(GenerateCodeFactory.CODE_PREFIX_statisticsId));
            reportFeeMonthStatisticsPo.setCommunityId(tmpReportFeeDto.getCommunityId());
            reportFeeMonthStatisticsPo.setConfigId(tmpReportFeeDto.getConfigId());
            reportFeeMonthStatisticsPo.setFeeCreateTime(DateUtil.getFormatTimeString(tmpReportFeeDto.getEndTime(), DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsPo.setFeeId(tmpReportFeeDto.getFeeId());
            reportFeeMonthStatisticsPo.setFeeMonth(DateUtil.getMonth() + "");
            reportFeeMonthStatisticsPo.setFeeYear(DateUtil.getYear() + "");
            reportFeeMonthStatisticsPo.setCurMaxTime(DateUtil.getNextMonthFirstDay(DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsPo.setObjId(tmpReportCarDto.getCarId());
            reportFeeMonthStatisticsPo.setObjType(FeeDto.PAYER_OBJ_TYPE_CAR);
            reportFeeMonthStatisticsPo.setFeeName(tmpReportFeeDto.getFeeName());
            reportFeeMonthStatisticsPo.setObjName(tmpReportCarDto.getCarNum() + "(" + tmpReportCarDto.getAreaNum() + "停车场" + tmpReportCarDto.getNum() + "车位)");
            reportFeeMonthStatisticsPo.setUpdateTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsServiceDaoImpl.saveReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsPo));
        }


        Date endTime = tmpReportFeeDto.getEndTime();

        Calendar calender = Calendar.getInstance();
        calender.setTime(endTime);
        int year = calender.get(Calendar.YEAR);
        int month = calender.get(Calendar.MONTH);

        ReportFeeMonthStatisticsPo tmpReportFeeMonthStatisticsPo = new ReportFeeMonthStatisticsPo();
        tmpReportFeeMonthStatisticsPo.setFeeId(tmpReportFeeDto.getFeeId());
        tmpReportFeeMonthStatisticsPo.setCurMaxTime(DateUtil.getFormatTimeString(endTime,DateUtil.DATE_FORMATE_STRING_A));
        tmpReportFeeMonthStatisticsPo.setOweAmount("0");
        reportFeeMonthStatisticsServiceDaoImpl.updateReportFeeMonthStatisticsOwe(BeanConvertUtil.beanCovertMap(tmpReportFeeMonthStatisticsPo));
    }

    /**
     * 解决上线前 欠费数据
     *
     * @param tmpReportCarDto
     * @param tmpReportFeeDto
     */
    private void dealBeforeUploadCarFee(ReportFeeDto tmpReportFeeDto, ReportCarDto tmpReportCarDto) {


        Calendar preMonthDate = Calendar.getInstance();
        preMonthDate.set(Calendar.DAY_OF_MONTH, 1);
        preMonthDate.add(Calendar.DAY_OF_MONTH, -1);

        //当月一日
        Calendar curMonthDate = Calendar.getInstance();
        curMonthDate.set(Calendar.DAY_OF_MONTH, 1);
        curMonthDate.set(Calendar.HOUR_OF_DAY, 0);
        curMonthDate.set(Calendar.MINUTE, 0);
        curMonthDate.set(Calendar.SECOND, 0);
        if (tmpReportFeeDto.getEndTime().getTime() > curMonthDate.getTime().getTime()) { //说明没有欠费
            return;
        }

        ReportFeeMonthStatisticsDto reportFeeMonthStatisticsDto = new ReportFeeMonthStatisticsDto();
        reportFeeMonthStatisticsDto.setCommunityId(tmpReportCarDto.getCommunityId());
        reportFeeMonthStatisticsDto.setConfigId(tmpReportFeeDto.getConfigId());
        reportFeeMonthStatisticsDto.setObjId(tmpReportFeeDto.getPayerObjId());
        reportFeeMonthStatisticsDto.setFeeId(tmpReportFeeDto.getFeeId());
        reportFeeMonthStatisticsDto.setObjType(tmpReportFeeDto.getPayerObjType());
        reportFeeMonthStatisticsDto.setFeeYear(preMonthDate.get(Calendar.YEAR) + "");
        reportFeeMonthStatisticsDto.setFeeMonth((preMonthDate.get(Calendar.MONTH) + 1) + "");
        List<ReportFeeMonthStatisticsDto> statistics = BeanConvertUtil.covertBeanList(
                reportFeeMonthStatisticsServiceDaoImpl.getReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsDto)),
                ReportFeeMonthStatisticsDto.class);
        //上个月有数据 不处理
        if (statistics != null && statistics.size() > 0) {
            return;
        }

        if (tmpReportFeeDto.getDeadlineTime().getTime() < curMonthDate.getTime().getTime()) {
            curMonthDate.setTime(tmpReportFeeDto.getDeadlineTime());
        }

        double receivableAmount = 0.0;
        if (FeeDto.FEE_FLAG_ONCE.equals(tmpReportFeeDto.getFeeFlag())) {
            receivableAmount = tmpReportFeeDto.getFeePrice();
        } else {
            double month = computeFeeSMOImpl.dayCompare(tmpReportFeeDto.getEndTime(), curMonthDate.getTime());
            BigDecimal curDegree = new BigDecimal(month);
            receivableAmount = curDegree.multiply(new BigDecimal(tmpReportFeeDto.getFeePrice())).setScale(2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        }


        ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo = new ReportFeeMonthStatisticsPo();
        reportFeeMonthStatisticsPo.setDeadlineTime(DateUtil.getFormatTimeString(curMonthDate.getTime(), DateUtil.DATE_FORMATE_STRING_A));

        reportFeeMonthStatisticsPo.setOweAmount(receivableAmount + "");
        reportFeeMonthStatisticsPo.setReceivedAmount("0");
        reportFeeMonthStatisticsPo.setReceivableAmount(receivableAmount + "");
        reportFeeMonthStatisticsPo.setStatisticsId(GenerateCodeFactory.getGeneratorId(GenerateCodeFactory.CODE_PREFIX_statisticsId));
        reportFeeMonthStatisticsPo.setCommunityId(tmpReportFeeDto.getCommunityId());
        reportFeeMonthStatisticsPo.setConfigId(tmpReportFeeDto.getConfigId());
        reportFeeMonthStatisticsPo.setFeeCreateTime(DateUtil.getFormatTimeString(tmpReportFeeDto.getEndTime(), DateUtil.DATE_FORMATE_STRING_A));
        reportFeeMonthStatisticsPo.setFeeId(tmpReportFeeDto.getFeeId());
        reportFeeMonthStatisticsPo.setFeeMonth((preMonthDate.get(Calendar.MONTH) + 1) + "");
        reportFeeMonthStatisticsPo.setFeeYear(preMonthDate.get(Calendar.YEAR) + "");
        reportFeeMonthStatisticsPo.setCurMaxTime(DateUtil.getFormatTimeString(DateUtil.getFirstDate(),DateUtil.DATE_FORMATE_STRING_A));
        reportFeeMonthStatisticsPo.setObjId(tmpReportCarDto.getCarId());
        reportFeeMonthStatisticsPo.setObjType(FeeDto.PAYER_OBJ_TYPE_CAR);
        reportFeeMonthStatisticsPo.setFeeName(tmpReportFeeDto.getFeeName());
        reportFeeMonthStatisticsPo.setObjName(tmpReportCarDto.getCarNum() + "(" + tmpReportCarDto.getAreaNum() + "停车场" + tmpReportCarDto.getNum() + "车位)");
        reportFeeMonthStatisticsPo.setUpdateTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
        reportFeeMonthStatisticsServiceDaoImpl.saveReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsPo));


    }

    /**
     * 处理费用
     *
     * @param reportRoomDto
     */
    private void doDealRoomFees(ReportRoomDto reportRoomDto) {
        ReportFeeDto reportFeeDto = new ReportFeeDto();
        reportFeeDto.setPayerObjId(reportRoomDto.getRoomId());
        reportFeeDto.setPayerObjType(FeeDto.PAYER_OBJ_TYPE_ROOM);
        //reportFeeDto.setState(FeeDto.STATE_DOING);
        List<ReportFeeDto> feeDtos = reportFeeServiceDaoImpl.getFees(reportFeeDto);

        if (feeDtos == null || feeDtos.size() < 1) {
            return;
        }

        for (ReportFeeDto tmpReportFeeDto : feeDtos) {
            try {
                doDealRoomFee(reportRoomDto, tmpReportFeeDto);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("处理房屋费用失败" + JSONObject.toJSONString(tmpReportFeeDto), e);
            }
        }


    }

    private void doDealRoomFee(ReportRoomDto reportRoomDto, ReportFeeDto tmpReportFeeDto) {

        //费用已经结束 并且当月实收为0 那就是 之前就结束了 无需处理  && ListUtil.isNull(statistics)
        if (FeeDto.STATE_FINISH.equals(tmpReportFeeDto.getState())
                && getCurFeeReceivedAmount(tmpReportFeeDto) == 0) {
            return;
        }

        ReportFeeMonthStatisticsDto reportFeeMonthStatisticsDto = new ReportFeeMonthStatisticsDto();
        reportFeeMonthStatisticsDto.setCommunityId(reportRoomDto.getCommunityId());
        reportFeeMonthStatisticsDto.setConfigId(tmpReportFeeDto.getConfigId());
        reportFeeMonthStatisticsDto.setObjId(tmpReportFeeDto.getPayerObjId());
        //reportFeeMonthStatisticsDto.setFeeId(tmpReportFeeDto.getFeeId());
        reportFeeMonthStatisticsDto.setObjType(tmpReportFeeDto.getPayerObjType());
        reportFeeMonthStatisticsDto.setFeeYear(DateUtil.getYear() + "");
        reportFeeMonthStatisticsDto.setFeeMonth(DateUtil.getMonth() + "");
        List<ReportFeeMonthStatisticsDto> statistics = BeanConvertUtil.covertBeanList(
                reportFeeMonthStatisticsServiceDaoImpl.getReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsDto)),
                ReportFeeMonthStatisticsDto.class);


        double receivedAmount = getReceivedAmount(tmpReportFeeDto); //实收

        FeeDto feeDto = BeanConvertUtil.covertBean(tmpReportFeeDto, FeeDto.class);
        Map<String, Object> targetEndDateAndOweMonth = computeFeeSMOImpl.getTargetEndDateAndOweMonth(feeDto, null);

        Date targetEndDate = (Date) targetEndDateAndOweMonth.get("targetEndDate");
        tmpReportFeeDto.setDeadlineTime(targetEndDate);
        double oweAmount = getOweAmount(tmpReportFeeDto, reportRoomDto, null); //欠费

        double receivableAmount = getReceivableAmount(tmpReportFeeDto, receivedAmount); //应收
        //解决上线时 之前欠费没有刷入导致费用金额对不上问题处理
        dealBeforeUploadRoomFee(reportRoomDto, tmpReportFeeDto, receivableAmount);



        ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo = new ReportFeeMonthStatisticsPo();
        reportFeeMonthStatisticsPo.setDeadlineTime(DateUtil.getFormatTimeString(targetEndDate, DateUtil.DATE_FORMATE_STRING_A));
        if (!ListUtil.isNull(statistics)) {
            ReportFeeMonthStatisticsDto statistic = statistics.get(0);
            reportFeeMonthStatisticsPo.setStatisticsId(statistic.getStatisticsId());
            //reportFeeMonthStatisticsPo.setReceivableAmount(receivableAmount + "");
            reportFeeMonthStatisticsPo.setReceivedAmount(receivedAmount + "");
            reportFeeMonthStatisticsPo.setOweAmount(oweAmount + "");
            reportFeeMonthStatisticsPo.setFeeId(tmpReportFeeDto.getFeeId());
            reportFeeMonthStatisticsPo.setUpdateTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsPo.setFeeName(StringUtil.isEmpty(tmpReportFeeDto.getImportFeeName()) ? tmpReportFeeDto.getFeeName() : tmpReportFeeDto.getImportFeeName());
            reportFeeMonthStatisticsServiceDaoImpl.updateReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsPo));
        } else {
            reportFeeMonthStatisticsPo.setOweAmount(oweAmount + "");
            reportFeeMonthStatisticsPo.setReceivedAmount(receivedAmount + "");
            reportFeeMonthStatisticsPo.setReceivableAmount(receivableAmount + "");
            reportFeeMonthStatisticsPo.setStatisticsId(GenerateCodeFactory.getGeneratorId(GenerateCodeFactory.CODE_PREFIX_statisticsId));
            reportFeeMonthStatisticsPo.setCommunityId(tmpReportFeeDto.getCommunityId());
            reportFeeMonthStatisticsPo.setConfigId(tmpReportFeeDto.getConfigId());
            reportFeeMonthStatisticsPo.setFeeCreateTime(DateUtil.getFormatTimeString(tmpReportFeeDto.getEndTime(), DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsPo.setFeeId(tmpReportFeeDto.getFeeId());
            reportFeeMonthStatisticsPo.setFeeMonth(DateUtil.getMonth() + "");
            reportFeeMonthStatisticsPo.setFeeYear(DateUtil.getYear() + "");
            reportFeeMonthStatisticsPo.setCurMaxTime(DateUtil.getNextMonthFirstDay(DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsPo.setObjId(reportRoomDto.getRoomId());
            reportFeeMonthStatisticsPo.setObjType(FeeDto.PAYER_OBJ_TYPE_ROOM);
            reportFeeMonthStatisticsPo.setFeeName(StringUtil.isEmpty(tmpReportFeeDto.getImportFeeName()) ? tmpReportFeeDto.getFeeName() : tmpReportFeeDto.getImportFeeName());
            if (RoomDto.ROOM_TYPE_ROOM.equals(reportRoomDto.getRoomType())) {
                reportFeeMonthStatisticsPo.setObjName(reportRoomDto.getFloorNum() + "栋" + reportRoomDto.getUnitNum() + "单元" + reportRoomDto.getRoomNum() + "室");
            } else {
                reportFeeMonthStatisticsPo.setObjName(reportRoomDto.getFloorNum() + "栋" + reportRoomDto.getRoomNum() + "室");
            }
            reportFeeMonthStatisticsPo.setUpdateTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
            reportFeeMonthStatisticsServiceDaoImpl.saveReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsPo));
        }


        //将缴费 到期时间之前得欠费刷为0
        Date endTime = tmpReportFeeDto.getEndTime();

        ReportFeeMonthStatisticsPo tmpReportFeeMonthStatisticsPo = new ReportFeeMonthStatisticsPo();
        tmpReportFeeMonthStatisticsPo.setFeeId(tmpReportFeeDto.getFeeId());
        tmpReportFeeMonthStatisticsPo.setCurMaxTime(DateUtil.getFormatTimeString(endTime,DateUtil.DATE_FORMATE_STRING_A));
        tmpReportFeeMonthStatisticsPo.setOweAmount("0");
        reportFeeMonthStatisticsServiceDaoImpl.updateReportFeeMonthStatisticsOwe(BeanConvertUtil.beanCovertMap(tmpReportFeeMonthStatisticsPo));
    }

    public static void main(String[] args) {
        ReportFeeDetailDto feeDetailDto = new ReportFeeDetailDto();
        feeDetailDto.setStartTime(DateUtil.getFormatTimeString(DateUtil.getFirstDate(), DateUtil.DATE_FORMATE_STRING_A));
        feeDetailDto.setEndTime(DateUtil.getFormatTimeString(DateUtil.getNextMonthFirstDate(), DateUtil.DATE_FORMATE_STRING_A));

        System.out.println(JSONObject.toJSONString(feeDetailDto));
    }

    /**
     * 解决上线前 欠费数据
     *
     * @param reportRoomDto
     * @param tmpReportFeeDto
     */
    private void dealBeforeUploadRoomFee(ReportRoomDto reportRoomDto, ReportFeeDto tmpReportFeeDto, double curMonthReceivableAmount) {


        Calendar preMonthDate = Calendar.getInstance();
        preMonthDate.set(Calendar.DAY_OF_MONTH, 1);
        preMonthDate.add(Calendar.DAY_OF_MONTH, -1);

        //当月一日
        Calendar curMonthDate = Calendar.getInstance();
        curMonthDate.set(Calendar.DAY_OF_MONTH, 1);
        curMonthDate.set(Calendar.HOUR_OF_DAY, 0);
        curMonthDate.set(Calendar.MINUTE, 0);
        curMonthDate.set(Calendar.SECOND, 0);
        if (tmpReportFeeDto.getEndTime().getTime() > curMonthDate.getTime().getTime()) { //说明没有欠费
            return;
        }

        if (tmpReportFeeDto.getDeadlineTime().getTime() < curMonthDate.getTime().getTime()) {
            curMonthDate.setTime(tmpReportFeeDto.getDeadlineTime());
        }

        ReportFeeMonthStatisticsDto reportFeeMonthStatisticsDto = new ReportFeeMonthStatisticsDto();
        reportFeeMonthStatisticsDto.setCommunityId(reportRoomDto.getCommunityId());
        reportFeeMonthStatisticsDto.setConfigId(tmpReportFeeDto.getConfigId());
        reportFeeMonthStatisticsDto.setObjId(tmpReportFeeDto.getPayerObjId());
        reportFeeMonthStatisticsDto.setFeeId(tmpReportFeeDto.getFeeId());
        reportFeeMonthStatisticsDto.setObjType(tmpReportFeeDto.getPayerObjType());
        reportFeeMonthStatisticsDto.setFeeYear(preMonthDate.get(Calendar.YEAR) + "");
        reportFeeMonthStatisticsDto.setFeeMonth((preMonthDate.get(Calendar.MONTH) + 1) + "");
        List<ReportFeeMonthStatisticsDto> statistics = BeanConvertUtil.covertBeanList(
                reportFeeMonthStatisticsServiceDaoImpl.getReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsDto)),
                ReportFeeMonthStatisticsDto.class);
        //上个月有数据 不处理
        if (statistics != null && statistics.size() > 0) {
            return;
        }

        double receivableAmount = 0.0;
        if (FeeDto.FEE_FLAG_ONCE.equals(tmpReportFeeDto.getFeeFlag())) {
            receivableAmount = tmpReportFeeDto.getFeePrice();
        } else {
            double month = computeFeeSMOImpl.dayCompare(tmpReportFeeDto.getEndTime(), curMonthDate.getTime());
            BigDecimal curDegree = new BigDecimal(month);
            receivableAmount = curDegree.multiply(new BigDecimal(tmpReportFeeDto.getFeePrice())).setScale(2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        }
        ReportFeeMonthStatisticsPo reportFeeMonthStatisticsPo = new ReportFeeMonthStatisticsPo();
        reportFeeMonthStatisticsPo.setDeadlineTime(DateUtil.getFormatTimeString(curMonthDate.getTime(), DateUtil.DATE_FORMATE_STRING_A));

        reportFeeMonthStatisticsPo.setOweAmount(receivableAmount + "");
        reportFeeMonthStatisticsPo.setReceivedAmount("0");
        reportFeeMonthStatisticsPo.setReceivableAmount(receivableAmount + "");
        reportFeeMonthStatisticsPo.setStatisticsId(GenerateCodeFactory.getGeneratorId(GenerateCodeFactory.CODE_PREFIX_statisticsId));
        reportFeeMonthStatisticsPo.setCommunityId(tmpReportFeeDto.getCommunityId());
        reportFeeMonthStatisticsPo.setConfigId(tmpReportFeeDto.getConfigId());
        reportFeeMonthStatisticsPo.setFeeCreateTime(DateUtil.getFormatTimeString(tmpReportFeeDto.getEndTime(), DateUtil.DATE_FORMATE_STRING_A));
        reportFeeMonthStatisticsPo.setFeeId(tmpReportFeeDto.getFeeId());
        reportFeeMonthStatisticsPo.setFeeMonth((preMonthDate.get(Calendar.MONTH) + 1) + "");
        reportFeeMonthStatisticsPo.setFeeYear(preMonthDate.get(Calendar.YEAR) + "");
        reportFeeMonthStatisticsPo.setCurMaxTime(DateUtil.getFormatTimeString(DateUtil.getFirstDate(),DateUtil.DATE_FORMATE_STRING_A));

        reportFeeMonthStatisticsPo.setObjId(reportRoomDto.getRoomId());
        reportFeeMonthStatisticsPo.setObjType(FeeDto.PAYER_OBJ_TYPE_ROOM);
        reportFeeMonthStatisticsPo.setFeeName(StringUtil.isEmpty(tmpReportFeeDto.getImportFeeName()) ? tmpReportFeeDto.getFeeName() : tmpReportFeeDto.getImportFeeName());
        if (RoomDto.ROOM_TYPE_ROOM.equals(reportRoomDto.getRoomType())) {
            reportFeeMonthStatisticsPo.setObjName(reportRoomDto.getFloorNum() + "栋" + reportRoomDto.getUnitNum() + "单元" + reportRoomDto.getRoomNum() + "室");
        } else {
            reportFeeMonthStatisticsPo.setObjName(reportRoomDto.getFloorNum() + "栋" + reportRoomDto.getRoomNum() + "室");
        }
        reportFeeMonthStatisticsPo.setUpdateTime(DateUtil.getNow(DateUtil.DATE_FORMATE_STRING_A));
        reportFeeMonthStatisticsServiceDaoImpl.saveReportFeeMonthStatisticsInfo(BeanConvertUtil.beanCovertMap(reportFeeMonthStatisticsPo));


    }

    /**
     * 当月欠费
     *
     * @param tmpReportFeeDto
     * @param receivedAmount
     * @return
     */
    private double getReceivableAmount(ReportFeeDto tmpReportFeeDto, double receivedAmount) {

        //一次性费用 除以月份 平均
        if (FeeDto.FEE_FLAG_ONCE.equals(tmpReportFeeDto.getFeeFlag())) {
            return computeOnceFee(tmpReportFeeDto);
        }
        return tmpReportFeeDto.getFeePrice();

    }

    /**
     * 获取当月实收
     *
     * @param tmpReportFeeDto
     * @return
     */
    private double getCurFeeReceivedAmount(ReportFeeDto tmpReportFeeDto) {
        ReportFeeDetailDto feeDetailDto = new ReportFeeDetailDto();
        feeDetailDto.setStartTime(DateUtil.getFormatTimeString(DateUtil.getFirstDate(), DateUtil.DATE_FORMATE_STRING_A));
        feeDetailDto.setEndTime(DateUtil.getFormatTimeString(DateUtil.getNextMonthFirstDate(), DateUtil.DATE_FORMATE_STRING_A));
        feeDetailDto.setFeeId(tmpReportFeeDto.getFeeId());

        double receivedAmount = reportFeeServiceDaoImpl.getFeeReceivedAmount(feeDetailDto);

        return receivedAmount;
    }

    /**
     * 获取当月实收
     *
     * @param tmpReportFeeDto
     * @return
     */
    private double getReceivedAmount(ReportFeeDto tmpReportFeeDto) {
        ReportFeeDetailDto feeDetailDto = new ReportFeeDetailDto();
        feeDetailDto.setStartTime(DateUtil.getFormatTimeString(DateUtil.getFirstDate(), DateUtil.DATE_FORMATE_STRING_A));
        feeDetailDto.setEndTime(DateUtil.getFormatTimeString(DateUtil.getNextMonthFirstDate(), DateUtil.DATE_FORMATE_STRING_A));
        feeDetailDto.setConfigId(tmpReportFeeDto.getConfigId());
        feeDetailDto.setPayerObjId(tmpReportFeeDto.getPayerObjId());

        double receivedAmount = reportFeeServiceDaoImpl.getFeeReceivedAmount(feeDetailDto);

        return receivedAmount;
    }

    /**
     * 获取当月应收
     *
     * @param tmpReportFeeDto
     * @return
     */
    private double getOweAmountByCar(ReportFeeDto tmpReportFeeDto, ReportRoomDto reportRoomDto, ReportCarDto reportCarDto) {

        double feePrice = computeFeeSMOImpl.getReportFeePrice(tmpReportFeeDto, reportRoomDto, reportCarDto);
        tmpReportFeeDto.setFeePrice(feePrice);
        BigDecimal feePriceDec = new BigDecimal(feePrice);

        if (DateUtil.getCurrentDate().getTime() < tmpReportFeeDto.getStartTime().getTime()) {
            return 0.0;
        }

        if (FeeDto.FEE_FLAG_ONCE.equals(tmpReportFeeDto.getFeeFlag())) {
            return computeOnceFee(tmpReportFeeDto);
        }
        OwnerCarDto ownerCarDto = new OwnerCarDto();
        ownerCarDto.setCommunityId(tmpReportFeeDto.getCommunityId());
        ownerCarDto.setCarId(tmpReportFeeDto.getCarId());
        List<OwnerCarDto> ownerCarDtos = ownerCarInnerServiceSMOImpl.queryOwnerCars(ownerCarDto);
        if (ownerCarDtos == null || ownerCarDtos.size() < 1) {
            return 0.0;
        }
        Date endTime = ownerCarDtos.get(0).getEndTime();

        //1.0 费用到期时间和费用结束时间 都不在当月
        if (!belongCurMonth(tmpReportFeeDto.getEndTime())
                && !belongCurMonth(endTime)
                && tmpReportFeeDto.getEndTime().getTime() < DateUtil.getFirstDate().getTime()) {
            return feePrice;
        }

        //2.0 费用到期时间 在当月，费用结束时间不在当月
        if (belongCurMonth(tmpReportFeeDto.getEndTime())
                && !belongCurMonth(endTime)) {
            //算天数
            double month = computeFeeSMOImpl.dayCompare(tmpReportFeeDto.getEndTime(), DateUtil.getNextMonthFirstDate());
            BigDecimal curDegree = new BigDecimal(month);
            return curDegree.multiply(feePriceDec).setScale(2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        }
        //3.0 费用到期时间 不在当月，费用结束时间在当月
        if (!belongCurMonth(tmpReportFeeDto.getEndTime())
                && belongCurMonth(endTime)) {
            //算天数
            double month = computeFeeSMOImpl.dayCompare(DateUtil.getFirstDate(), tmpReportFeeDto.getConfigEndTime());
            BigDecimal curDegree = new BigDecimal(month);
            return curDegree.multiply(feePriceDec).setScale(2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        }
        return 0.0;
    }

    private double computeOnceFee(ReportFeeDto tmpReportFeeDto) {
        Date nowTime = DateUtil.getCurrentDate();
        if (tmpReportFeeDto.getEndTime().getTime() > nowTime.getTime()
                || tmpReportFeeDto.getDeadlineTime().getTime() < nowTime.getTime()) {
            return 0.0;
        }
        double month = computeFeeSMOImpl.dayCompare(tmpReportFeeDto.getDeadlineTime(), tmpReportFeeDto.getEndTime());
        month = Math.ceil(month);

        BigDecimal feePriceDec = new BigDecimal(tmpReportFeeDto.getFeePrice());
        double money = feePriceDec.divide(new BigDecimal(month),2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        return money;
    }

    /**
     * 获取当月应收
     *
     * @param tmpReportFeeDto
     * @return
     */
    private double getOweAmount(ReportFeeDto tmpReportFeeDto, ReportRoomDto reportRoomDto, ReportCarDto reportCarDto) {

        double feePrice = computeFeeSMOImpl.getReportFeePrice(tmpReportFeeDto, reportRoomDto, reportCarDto);
        tmpReportFeeDto.setFeePrice(feePrice);
        BigDecimal feePriceDec = new BigDecimal(feePrice);

        if (DateUtil.getCurrentDate().getTime() < tmpReportFeeDto.getStartTime().getTime()) {
            return 0.0;
        }

        if (FeeDto.FEE_FLAG_ONCE.equals(tmpReportFeeDto.getFeeFlag())) {
            return computeOnceFee(tmpReportFeeDto);
        }

        //1.0 费用到期时间和费用结束时间 都不在当月
        if (!belongCurMonth(tmpReportFeeDto.getEndTime())
                && !belongCurMonth(tmpReportFeeDto.getConfigEndTime())
                && tmpReportFeeDto.getEndTime().getTime() < DateUtil.getFirstDate().getTime()) {
            return feePrice;
        }

        //2.0 费用到期时间 在当月，费用结束时间不在当月
        if (belongCurMonth(tmpReportFeeDto.getEndTime())
                && !belongCurMonth(tmpReportFeeDto.getConfigEndTime())) {
            //算天数
            double month = computeFeeSMOImpl.dayCompare(tmpReportFeeDto.getEndTime(), DateUtil.getNextMonthFirstDate());
            BigDecimal curDegree = new BigDecimal(month);
            return curDegree.multiply(feePriceDec).setScale(2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        }
        //3.0 费用到期时间 不在当月，费用结束时间在当月
        if (!belongCurMonth(tmpReportFeeDto.getEndTime())
                && belongCurMonth(tmpReportFeeDto.getConfigEndTime())) {
            //算天数
            double month = computeFeeSMOImpl.dayCompare(DateUtil.getFirstDate(), tmpReportFeeDto.getConfigEndTime());
            BigDecimal curDegree = new BigDecimal(month);
            return curDegree.multiply(feePriceDec).setScale(2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        }
        return 0.0;
    }

    private boolean belongCurMonth(Date date) {
        if (DateUtil.belongCalendar(date, DateUtil.getFirstDate(), DateUtil.getNextMonthFirstDate())) {
            return true;
        }
        return false;
    }
}
