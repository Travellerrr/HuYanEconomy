package cn.chahuyun.economy.manager;

import cn.chahuyun.economy.entity.rob.RobInfo;
import cn.chahuyun.economy.utils.EconomyUtil;
import cn.chahuyun.economy.utils.ShareUtils;
import cn.chahuyun.economy.utils.TimeConvertUtil;
import cn.chahuyun.hibernateplus.HibernateFactory;
import cn.hutool.core.util.RandomUtil;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.Message;

import java.util.Date;

import static cn.chahuyun.economy.HuYanEconomy.robConfig;

/**
 * 抢劫管理
 *
 * @author Moyuyanli
 * @date 2022/11/15 10:01
 */
public class RobManager {
    // 抢劫失败消息变量
    static String[] robFailedVariable = {"${对象}"};
    // 抢劫成功消息变量
    static String[] robSuccessVariable = {"${对象}", "${金币}"};
    // 抢劫赔钱消息变量
    static String[] loseMoneyVariable = {"${对象}", "${金币}"};
    // 抢劫入狱消息变量
    static String[] robJailVariable = {"${对象}", "${金币}", "${时间}"};

    /**
     * 抢劫其他玩家
     * @param event 群消息事件
     */
    public static void robOther(GroupMessageEvent event) {
        // 获取发送者
        User sender = event.getSender();
        // 获取群组
        Group group = event.getGroup();
        // 获取消息主体
        Contact subject = event.getSubject();

        // 获取抢劫信息
        RobInfo robInfo = HibernateFactory.selectOne(RobInfo.class, sender.getId());

        if (checkCoolDown(subject, sender, robInfo)) return;

        // 获取消息内容
        String message = event.getMessage().contentToString();
        // 获取被抢劫者的ID
        long victimId = Long.parseLong(message.split("@")[1].trim());

        // 判断是否抢劫自己
        if (sender.getId() == victimId) {
            group.sendMessage("你不能抢劫自己哦！");
            return;
        }
        // 获取被抢劫者
        User victim = group.get(victimId);
        // 判断被抢劫者是否在群内
        if (victim == null) {
            subject.sendMessage("该用户不在群内！");
            return;
        }

        // 获取被抢劫者的金币数量
        double victimHasMoney = EconomyUtil.getMoneyByUser(victim);

        // 判断被抢劫者是否有足够的金币
        if (victimHasMoney <= 1) {
            subject.sendMessage("该用户没有足够的金币！");
            return;
        }

        // 计算抢劫成功率
        int chance = RandomUtil.randomInt(0, 101);

        // 计算抢劫金额
        double robMoney = Math.round(RandomUtil.randomDouble(0.1, Math.min(victimHasMoney/2, robConfig.getRobMaxMoney()))*10.0)/10.0;

        String victimName = victim.getNick();

        // 判断是否被抓
        if (getInJail(subject, sender, robInfo, chance, victimName, robMoney)) return;

        if (loseMoney(subject, sender, chance, 50, victimName, robMoney)) return;

        // 更新抢劫信息
        updateRobInfo(sender, robInfo, robConfig.getRobCoolTime(),false);
        // 判断是否抢劫失败
        if (robFailed(subject, sender, chance, 75, victimName)) return;


        // 扣除被抢劫者的金币
        EconomyUtil.plusMoneyToUser(victim, -robMoney);
        // 增加抢劫者的金币
        EconomyUtil.plusMoneyToUser(sender, robMoney);

        // 获取抢劫成功消息
        int msgIndex = RandomUtil.randomInt(0, robConfig.getRobSuccessMsg().size());
        String msg = robConfig.getRobSuccessMsg().get(msgIndex);
        // 替换消息中的变量
        msg = ShareUtils.replacer(msg, robSuccessVariable, victim.getNick(), robMoney);

        // 发送消息
        subject.sendMessage(new At(sender.getId()).plus("\n" + msg));
    }

    /**
     * 抢劫银行
     * @param event 群消息事件
     */
    public static void robBank(GroupMessageEvent event) {
        // 获取发送者
        User sender = event.getSender();
        // 获取消息主体
        Contact subject = event.getSubject();

        // 获取抢劫信息
        RobInfo robInfo = HibernateFactory.selectOne(RobInfo.class, sender.getId());

        if (checkCoolDown(subject, sender, robInfo)) return;

        // 计算抢劫成功率
        int chance = RandomUtil.randomInt(0, 101);

        // 计算抢劫金额
        double robMoney = Math.round(RandomUtil.randomDouble(1000, 5000)*10.0)/10.0;

        // 判断是否被抓
        if (getInJail(subject, sender, robInfo, chance, "银行", robMoney)) return;

        // 更新抢劫信息
        updateRobInfo(sender, robInfo, robConfig.getRobCoolTime(),false);
        // 判断是否抢劫失败
        if (robFailed(subject, sender, chance, 99, "银行")) return;

        // 增加抢劫者的金币
        EconomyUtil.plusMoneyToUser(sender, robMoney);

        // 获取抢劫成功消息
        int msgIndex = RandomUtil.randomInt(0, robConfig.getRobSuccessMsg().size());
        String msg = robConfig.getRobSuccessMsg().get(msgIndex);
        // 替换消息中的变量
        msg = ShareUtils.replacer(msg, robSuccessVariable, "银行", robMoney);

        // 发送消息
        subject.sendMessage(new At(sender.getId()).plus("\n" + msg));
    }

    /**
     * 更新抢劫信息
     * @param sender 抢劫者
     * @param robInfo 该用户抢劫信息
     * @param isCauseJail 是否因为入狱
     */
    private static void updateRobInfo(User sender, RobInfo robInfo, long cooldown, boolean isCauseJail) {
        // 判断抢劫信息是否为空
        if (robInfo == null) {
            // 创建抢劫信息
            robInfo = RobInfo.builder()
                    .userId(sender.getId())
                    .lastRobTime(new Date())
                    .cooldown(cooldown)
                    .isInJail(isCauseJail)
                    .build();
        } else {
            // 更新抢劫信息
            robInfo.setLastRobTime(new Date());
            robInfo.setCooldown(cooldown);
            robInfo.setInJail(isCauseJail);
        }

        // 保存抢劫信息
        HibernateFactory.merge(robInfo);
    }

    /**
     * 重置抢夺冷却，将监狱信息设为<code>false</code>
     * @param event 消息事件
     */
    public static void release(MessageEvent event) {
        RobInfo robInfo = HibernateFactory.selectOne(RobInfo.class, event.getSender().getId());
        updateRobInfo(event.getSender(), robInfo,0 ,false);
        event.getSubject().sendMessage(new At(event.getSender().getId()).plus("\n你已成功释放！"));
    }

    /**
     * 检查用户是否在冷却中或是否在监狱中
     *
     * @param subject 消息主体
     * @param sender  发送者
     * @param robInfo 抢劫信息
     * @return 如果用户在冷却中或监狱中，发送消息并返回 true，否则返回 false
     */
    private static boolean checkCoolDown(Contact subject, User sender, RobInfo robInfo) {

        // 获取当前时间
        Date now = new Date();
        // 判断是否在冷却中
        if (robInfo != null && (now.getTime() - robInfo.getLastRobTime().getTime()) < robInfo.getCooldown()*1000) {
            // 计算剩余冷却时间
            long remainingCooldown = (robInfo.getCooldown() * 1000 - (now.getTime() - robInfo.getLastRobTime().getTime())) / 1000;
            // 判断是否在监狱中
            // 发送消息
            Message msg = robInfo.isInJail() ? new At(sender.getId()).plus("\n你还在监狱里！\n请等待" + TimeConvertUtil.secondConvert(remainingCooldown) +"释放出狱") : new At(sender.getId()).plus("\n你还在冷却中！\n请等待" + TimeConvertUtil.secondConvert(remainingCooldown) +"后再次抢劫");
            subject.sendMessage(msg);
            return true;
        }

        if (EconomyUtil.getMoneyByUser(sender) <= 0) {
            subject.sendMessage(new At(sender.getId()).plus(robConfig.getRobNotEnoughMsg()));
            return true;
        }

        return false;

    }

    /**
     * 判断用户是否被抓
     *
     * @param subject 消息主体
     * @param sender  发送者
     * @param robInfo 抢劫信息
     * @param chance  抢劫成功率
     * @param victimName 被抢劫者的名字
     * @param robMoney 抢劫金额
     * @return 如果用户被抓，发送消息并返回 true，否则返回 false
     */
    private static boolean getInJail(Contact subject, User sender, RobInfo robInfo, int chance, String victimName, double robMoney) {
        if (chance <= 50) {
            // 获取抢劫入狱消息
            int msgIndex = RandomUtil.randomInt(0, robConfig.getRobJailMsg().size());
            String msg = robConfig.getRobJailMsg().get(msgIndex);

            //扣除抢劫者金币
            EconomyUtil.plusMoneyToUser(sender, -robMoney);

            // 替换消息中的变量
            msg = ShareUtils.replacer(msg, robJailVariable, victimName, robMoney, TimeConvertUtil.secondConvert(robConfig.getJailCoolTime()));
            // 发送消息
            subject.sendMessage(new At(sender.getId()).plus("\n你被警察抓到了！\n" + msg));

            // 更新抢劫信息
            updateRobInfo(sender, robInfo, robConfig.getJailCoolTime(), true);
            return true;
        }
        return false;
    }

    /**
     * 判断用户是否抢劫失败
     *
     * @param subject 消息主体
     * @param sender  发送者
     * @param chance  抢劫成功率
     * @param failedChance 失败率
     * @param victimName 被抢劫者的名字
     * @return 如果用户抢劫失败，发送消息并返回 true，否则返回 false
     */
    private static boolean robFailed(Contact subject, User sender, int chance, int failedChance, String victimName) {
        if (chance <= failedChance) {
            int msgIndex = RandomUtil.randomInt(0, robConfig.getRobFailMsg().size());
            String msg = robConfig.getRobFailMsg().get(msgIndex);
            // 替换消息中的变量
            msg = ShareUtils.replacer(msg, robFailedVariable, victimName);
            // 发送消息
            subject.sendMessage(new At(sender.getId()).plus("\n你失败了！\n" + msg));
            return true;
        }
        return false;
    }

    /**
     * 判断用户是否赔钱
     *
     * @param subject 消息主体
     * @param sender  发送者
     * @param chance  抢劫成功率
     * @param loseChance 赔钱率
     * @param victimName 被抢劫者的名字
     * @param robMoney 抢劫金额
     * @return 如果用户赔钱，发送消息并返回 true，否则返回 false
     */
    private static boolean loseMoney(Contact subject, User sender, int chance, int loseChance, String victimName, double robMoney) {
        if (chance <= loseChance) {
            int msgIndex = RandomUtil.randomInt(0, robConfig.getLoseMoneyMsg().size());
            String msg = robConfig.getLoseMoneyMsg().get(msgIndex);
            // 替换消息中的变量
            msg = ShareUtils.replacer(msg, loseMoneyVariable, victimName, robMoney);
            //扣除抢劫者对应金钱
            EconomyUtil.plusMoneyToUser(sender, -robMoney);
            // 发送消息
            subject.sendMessage(new At(sender.getId()).plus("\n你失败了！\n" + msg));
            return true;
        }
        return false;
    }
}