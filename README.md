# Stellaris mod path annotation - IntelliJ IDEA Plugin

一个用于管理和浏览 Steam Workshop 中 Stellaris 模组的 IntelliJ IDEA 插件。

## 功能特性

- ✅ 自动检测 Steam Workshop 目录
- ✅ 读取 descriptor.mod 文件提取模组信息
- ✅ 颜色编码显示模组信息
  - 文件夹名：白色
  - 模组名称：棕色
  - 版本号：灰白色
  - 支持版本：绿色
- ✅ 点击模组弹窗确认打开项目
- ✅ 异步加载，不阻塞 UI
- ✅ 一键刷新模组列表

## 构建方法

gradle buildPlugin

构建后的插件位于 `build/distributions/` 目录下。

## 安装方法

1. 打开 IntelliJ IDEA
2. 进入 `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk`
3. 选择生成的 `.zip` 文件
4. 重启 IDE

## 使用方法

1. 打开右侧工具栏中的 "Stellaris mod 路径标注："
2. 插件会自动扫描 Steam Workshop 目录
3. 点击任意模组可打开该项目

## 系统要求

- IntelliJ IDEA 2023.1 或更高版本
- Java 11 或更高版本

## 许可证

MIT License
