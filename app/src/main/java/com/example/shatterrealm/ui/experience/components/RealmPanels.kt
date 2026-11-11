package com.example.shatterrealm.ui.experience.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.input.pointer.pointerInput
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.shatterrealm.R
import com.example.shatterrealm.ui.experience.*
import com.example.shatterrealm.ui.theme.RealmStyle as C
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter

/** Official SpatialUI tap data distinguishes hands from controller pointers before dispatch. */
@Composable private fun Modifier.handClick(enabled:Boolean,onClick:()->Unit):Modifier {
    val context=LocalContext.current
    return semantics { role=Role.Button }.pointerInput(enabled,onClick) {
        detectSpatialTapGesture(context) { tap ->
            if(enabled && tap.interactionKind in setOf(InteractionKind.Poke,InteractionKind.DirectPinch,
                InteractionKind.GazePinch,InteractionKind.RayBasedPinch)) onClick()
        }
    }
}
@Composable private fun meters(value: Float) = LocalPhysicalLengthConverter.current.lengthToDp(value,LengthUnit.Meters)
@Composable private fun Panel(width: Float, modifier: Modifier = Modifier, content: @Composable ColumnScope.()->Unit) {
    Column(modifier.width(meters(width)).clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(C.Ink,C.Tile)))
        .padding(24.dp), content=content)
}
@Composable private fun Caption(text: String, accent: Boolean = false) {
    Text(text,color=if(accent) C.Mint else C.muted,fontSize=22.sp)
}
@Composable private fun Action(text: String, enabled: Boolean, onClick: ()->Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min=68.dp).clip(RoundedCornerShape(18.dp))
        .background(if(enabled) C.Mint else C.Tile).spatialHoverEffect()
        .handClick(enabled,onClick).padding(18.dp),contentAlignment=Alignment.Center) {
        Text(text,color=if(enabled) C.Ink else C.muted,fontSize=26.sp,fontWeight=FontWeight.Medium)
    }
}
@Composable fun Admission(state: ExperienceUiState, chinese: Boolean, onEvent:(ExperienceEvent)->Unit) {
    Panel(.62f) {
        Image(painterResource(R.drawable.mark),contentDescription=null,modifier=Modifier.size(88.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Text(if(chinese) "碎境" else "Shatter Realm",color=C.Ivory,fontSize=38.sp,fontWeight=FontWeight.Medium)
        if(!state.logo) {
            Spacer(Modifier.height(24.dp))
            Text(if(chinese) "安全提示" else "Before you begin",color=C.Mint,fontSize=30.sp)
            Spacer(Modifier.height(18.dp))
            Text(if(chinese) "本应用仅供娱乐解压，请勿在真实世界模仿投掷易碎物品行为，请注意周边环境安全" else
                "This app is for entertainment and stress relief only. Do not imitate throwing fragile objects in the real world. Stay aware of your surroundings.",
                color=C.Ivory,fontSize=26.sp,lineHeight=38.sp)
            Spacer(Modifier.height(28.dp))
            Action(if(chinese) "同意" else "Agree",state.handsTracked && !state.controllerActive) { onEvent(ExperienceEvent.Agree) }
            Spacer(Modifier.height(12.dp))
            Action(if(chinese) "退出" else "Exit",state.handsTracked && !state.controllerActive) { onEvent(ExperienceEvent.Exit) }
            if(!state.handsTracked) Caption(if(chinese) "请抬起双手，使用手势操作" else "Raise your hands to use hand tracking")
        }
    }
}
@Composable fun ControllerNotice(chinese: Boolean) {
    Panel(.50f) {
        Text(if(chinese) "请放下手柄" else "Put down your controllers",color=C.Ivory,fontSize=32.sp)
        Spacer(Modifier.height(20.dp))
        Text(if(chinese) "碎境仅使用手势交互。放下手柄并抬起双手，即可继续体验。" else
            "Shatter Realm uses hand tracking. Put down your controllers and raise your hands to continue.",color=C.muted,fontSize=25.sp,lineHeight=36.sp)
    }
}
@Composable fun Counter(state: ExperienceUiState,chinese: Boolean,label:(String)->String) {
    Panel(if(state.counts.byItem.isEmpty()) .22f else .44f) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.widthIn(min=115.dp)) {
                Caption(if(chinese) "总投掷数量" else "Total throws")
                Text(state.counts.total.toString(),color=C.Ivory,fontSize=46.sp,fontWeight=FontWeight.Light)
            }
            if(state.counts.byItem.isNotEmpty()) {
                Spacer(Modifier.width(22.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(26.dp)) {
                    state.counts.byItem.forEach { (id,count) -> Column {
                        Caption(label(id)); Text(count.toString(),color=C.Mint,fontSize=34.sp)
                    } }
                }
            }
        }
    }
}
@Composable fun Shelf(state: ExperienceUiState,chinese:Boolean,onEvent:(ExperienceEvent)->Unit) {
    val context=LocalContext.current
    Panel(.48f) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(chinese) "选择物品" else "Choose an object",color=C.Ivory,fontSize=26.sp)
            Caption(if(chinese) "触碰并左右滑动" else "Touch and swipe")
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            state.items.forEach { item ->
                Column(Modifier.width(124.dp).clip(RoundedCornerShape(16.dp))
                    .background(if(state.selected==item.id) C.Edge else C.Tile).spatialHoverEffect()
                    .handClick(!state.busy && !state.controllerActive && state.planeCount>0) { onEvent(ExperienceEvent.Select(item.id)) }
                    .padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    val icon=remember(item.id) { context.resources.getIdentifier("item_"+item.id.replace('-','_'),"drawable",context.packageName) }
                    Image(painterResource(if(icon!=0) icon else R.drawable.mark),item.label(chinese),modifier=Modifier.size(88.dp))
                    Text(item.label(chinese),color=C.Ivory,fontSize=19.sp,maxLines=2)
                }
            }
            listOf(Triple(R.drawable.refresh,if(chinese) "刷新" else "Refresh",ExperienceEvent.Refresh),
                Triple(R.drawable.reset,if(chinese) "重置" else "Reset",ExperienceEvent.Reset)).forEach { (icon,title,event) ->
                Column(Modifier.width(110.dp).clip(RoundedCornerShape(16.dp)).background(C.Tile).spatialHoverEffect()
                    .handClick(!state.controllerActive && (event==ExperienceEvent.Reset || !state.busy)) { onEvent(event) }
                    .padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Image(painterResource(icon),title,modifier=Modifier.size(76.dp));Spacer(Modifier.height(8.dp));Caption(title)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Caption(when {
            state.busy -> if(chinese) "正在准备物品…" else "Preparing objects…"
            state.loadError -> if(chinese) "物品加载失败，请重新选择" else "Could not load this object. Choose again."
            state.failedFiles.isNotEmpty() -> (if(chinese) "未能导入：" else "Could not import: ")+state.failedFiles.joinToString()
            state.planeCount==0 -> when(state.roomPlaneStatus) {
                RoomPlaneStatus.PAUSED -> if(chinese) "空间识别已暂停，请保持应用在前台并佩戴头显" else "Room tracking paused. Wear your headset and keep the app active."
                RoomPlaneStatus.UNAVAILABLE -> if(chinese) "系统空间识别不可用，请检查头显空间设置" else "Room tracking unavailable. Check your headset room settings."
                RoomPlaneStatus.ERROR -> if(chinese) "空间数据读取失败，正在重试" else "Could not read room data. Retrying…"
                RoomPlaneStatus.EMPTY -> if(chinese) "尚无真实地面或桌面数据，请先完成并保存系统房间扫描" else "No floor or table data. Complete and save room scanning in system settings."
                else -> if(chinese) "正在连接系统空间识别…" else "Connecting to system room tracking…"
            }
            !state.handsTracked -> if(chinese) "请抬起双手" else "Raise your hands"
            else -> if(chinese) "选择物品 · 捏合拾取 · 挥手松开" else "Choose · Pinch to grab · Swing and release"
        })
    }
}

@Composable fun ResetNotice(state:ExperienceUiState,chinese:Boolean,onEvent:(ExperienceEvent)->Unit) {
    Panel(.50f) {
        Text(if(chinese) "重置碎境？" else "Reset your realm?",color=C.Ivory,fontSize=32.sp)
        Spacer(Modifier.height(18.dp))
        Text(if(chinese) "清空所有物品和碎片，并将投掷计数归零。" else "Clear all objects and fragments, and reset every throw count.",color=C.muted,fontSize=25.sp)
        Spacer(Modifier.height(24.dp))
        Action(if(chinese) "确认重置" else "Reset",state.handsTracked && !state.controllerActive) { onEvent(ExperienceEvent.ConfirmReset) }
        Spacer(Modifier.height(12.dp))
        Action(if(chinese) "取消" else "Cancel",state.handsTracked && !state.controllerActive) { onEvent(ExperienceEvent.CancelReset) }
    }
}
