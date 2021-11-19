package com.java110.api.components.owner;

import com.java110.core.context.IPageData;
import com.java110.api.smo.IOwnerServiceSMO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 查询楼列表组件
 */
@Component("viewOwner")
public class ViewOwnerComponent {

    @Autowired
    private IOwnerServiceSMO ownerServiceSMOImpl;

    /**
     * 查询小区楼信息
     *
     * @param pd 页面封装对象 包含页面请求数据
     * @return ResponseEntity对象返回给页面
     */
    public ResponseEntity<String> getOwner(IPageData pd) {

        return ownerServiceSMOImpl.listOwner(pd);
    }


    public IOwnerServiceSMO getOwnerServiceSMOImpl() {
        return ownerServiceSMOImpl;
    }

    public void setOwnerServiceSMOImpl(IOwnerServiceSMO ownerServiceSMOImpl) {
        this.ownerServiceSMOImpl = ownerServiceSMOImpl;
    }
}
