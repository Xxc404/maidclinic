package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.maidclinic.registry.ModItems;

/**
 * 东方小女仆的附属入口：把 4 级医师徽章注册为女仆饰品。
 * <p>
 * 该类只会在 TLM 存在时由 TLM 的注解扫描加载；未安装 TLM 时不会被触碰。
 */
@LittleMaidExtension
public class MaidClinicExtension implements ILittleMaid {

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.DOCTOR_BADGE_4.get(), new DoctorBauble(4));
        manager.bind(ModItems.SUPPORT_KIT.get(), new SupportBauble());
    }
}
