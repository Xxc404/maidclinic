package com.maidclinic.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * 《家庭护理手册》的静态内容。
 * <p>
 * 使用可翻译组件而不是写死字符串，因此中文/英文共用同一本书；
 * 内容只讲通用急救与用药常识，并明确声明不构成医疗建议。
 */
public final class ManualContent {
    private ManualContent() {
    }

    public static final int PAGE_COUNT = 17;

    public static WrittenBookContent create() {
        List<Filterable<Component>> pages = new ArrayList<>(PAGE_COUNT);
        for (int i = 1; i <= PAGE_COUNT; i++) {
            pages.add(Filterable.passThrough(Component.translatable("book.maidclinic.page." + i)));
        }
        return new WrittenBookContent(
                Filterable.passThrough("女仆诊所 · 家庭护理手册"),
                "女仆诊所",
                0,
                pages,
                true);
    }
}
