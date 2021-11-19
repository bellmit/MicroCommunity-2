package com.java110.community.listener.inspectionRoute;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.java110.po.inspection.InspectionRoutePo;
import com.java110.utils.constant.BusinessTypeConstant;
import com.java110.utils.constant.StatusConstant;
import com.java110.utils.util.Assert;
import com.java110.community.dao.IInspectionRouteServiceDao;
import com.java110.core.annotation.Java110Listener;
import com.java110.core.context.DataFlowContext;
import com.java110.core.factory.GenerateCodeFactory;
import com.java110.entity.center.Business;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 保存 巡检路线信息 侦听
 * Created by wuxw on 2018/5/18.
 */
@Java110Listener("saveInspectionRouteInfoListener")
@Transactional
public class SaveInspectionRouteInfoListener extends AbstractInspectionRouteBusinessServiceDataFlowListener {

    private static Logger logger = LoggerFactory.getLogger(SaveInspectionRouteInfoListener.class);

    @Autowired
    private IInspectionRouteServiceDao inspectionRouteServiceDaoImpl;

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getBusinessTypeCd() {
        return BusinessTypeConstant.BUSINESS_TYPE_SAVE_INSPECTION_ROUTE;
    }

    /**
     * 保存巡检路线信息 business 表中
     *
     * @param dataFlowContext 数据对象
     * @param business        当前业务对象
     */
    @Override
    protected void doSaveBusiness(DataFlowContext dataFlowContext, Business business) {
        JSONObject data = business.getDatas();
        Assert.notEmpty(data, "没有datas 节点，或没有子节点需要处理");

        //处理 businessInspectionRoute 节点
        if (data.containsKey(InspectionRoutePo.class.getSimpleName())) {
            Object bObj = data.get(InspectionRoutePo.class.getSimpleName());
            JSONArray businessInspectionRoutes = null;
            if (bObj instanceof JSONObject) {
                businessInspectionRoutes = new JSONArray();
                businessInspectionRoutes.add(bObj);
            } else {
                businessInspectionRoutes = (JSONArray) bObj;
            }
            //JSONObject businessInspectionRoute = data.getJSONObject("businessInspectionRoute");
            for (int bInspectionRouteIndex = 0; bInspectionRouteIndex < businessInspectionRoutes.size(); bInspectionRouteIndex++) {
                JSONObject businessInspectionRoute = businessInspectionRoutes.getJSONObject(bInspectionRouteIndex);
                doBusinessInspectionRoute(business, businessInspectionRoute);
                if (bObj instanceof JSONObject) {
                    dataFlowContext.addParamOut("inspectionRouteId", businessInspectionRoute.getString("inspectionRouteId"));
                }
            }
        }
    }

    /**
     * business 数据转移到 instance
     *
     * @param dataFlowContext 数据对象
     * @param business        当前业务对象
     */
    @Override
    protected void doBusinessToInstance(DataFlowContext dataFlowContext, Business business) {
        JSONObject data = business.getDatas();

        Map info = new HashMap();
        info.put("bId", business.getbId());
        info.put("operate", StatusConstant.OPERATE_ADD);

        //巡检路线信息
        List<Map> businessInspectionRouteInfo = inspectionRouteServiceDaoImpl.getBusinessInspectionRouteInfo(info);
        if (businessInspectionRouteInfo != null && businessInspectionRouteInfo.size() > 0) {
            reFreshShareColumn(info, businessInspectionRouteInfo.get(0));
            inspectionRouteServiceDaoImpl.saveInspectionRouteInfoInstance(info);
            if (businessInspectionRouteInfo.size() == 1) {
                dataFlowContext.addParamOut("inspectionRouteId", businessInspectionRouteInfo.get(0).get("inspection_routeId"));
            }
        }
    }


    /**
     * 刷 分片字段
     *
     * @param info         查询对象
     * @param businessInfo 小区ID
     */
    private void reFreshShareColumn(Map info, Map businessInfo) {

        if (info.containsKey("communityId")) {
            return;
        }

        if (!businessInfo.containsKey("community_id")) {
            return;
        }

        info.put("communityId", businessInfo.get("community_id"));
    }

    /**
     * 撤单
     *
     * @param dataFlowContext 数据对象
     * @param business        当前业务对象
     */
    @Override
    protected void doRecover(DataFlowContext dataFlowContext, Business business) {
        String bId = business.getbId();
        //Assert.hasLength(bId,"请求报文中没有包含 bId");
        Map info = new HashMap();
        info.put("bId", bId);
        info.put("statusCd", StatusConstant.STATUS_CD_VALID);
        Map paramIn = new HashMap();
        paramIn.put("bId", bId);
        paramIn.put("statusCd", StatusConstant.STATUS_CD_INVALID);
        //巡检路线信息
        List<Map> inspectionRouteInfo = inspectionRouteServiceDaoImpl.getInspectionRouteInfo(info);
        if (inspectionRouteInfo != null && inspectionRouteInfo.size() > 0) {
            reFreshShareColumn(paramIn, inspectionRouteInfo.get(0));
            inspectionRouteServiceDaoImpl.updateInspectionRouteInfoInstance(paramIn);
        }
    }


    /**
     * 处理 businessInspectionRoute 节点
     *
     * @param business                总的数据节点
     * @param businessInspectionRoute 巡检路线节点
     */
    private void doBusinessInspectionRoute(Business business, JSONObject businessInspectionRoute) {

        Assert.jsonObjectHaveKey(businessInspectionRoute, "inspectionRouteId", "businessInspectionRoute 节点下没有包含 inspectionRouteId 节点");

        if (businessInspectionRoute.getString("inspectionRouteId").startsWith("-")) {
            //刷新缓存
            //flushInspectionRouteId(business.getDatas());

            businessInspectionRoute.put("inspectionRouteId", GenerateCodeFactory.getGeneratorId(GenerateCodeFactory.CODE_PREFIX_inspectionRouteId));

        }

        businessInspectionRoute.put("bId", business.getbId());
        businessInspectionRoute.put("operate", StatusConstant.OPERATE_ADD);
        //保存巡检路线信息
        inspectionRouteServiceDaoImpl.saveBusinessInspectionRouteInfo(businessInspectionRoute);

    }

    public IInspectionRouteServiceDao getInspectionRouteServiceDaoImpl() {
        return inspectionRouteServiceDaoImpl;
    }

    public void setInspectionRouteServiceDaoImpl(IInspectionRouteServiceDao inspectionRouteServiceDaoImpl) {
        this.inspectionRouteServiceDaoImpl = inspectionRouteServiceDaoImpl;
    }
}
