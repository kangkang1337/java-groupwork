package com.pixelforge.command;

import com.pixelforge.model.Document;

/**
 * 可撤销命令。
 *
 * <p>命令只保存"怎么恢复"所需的最小数据，不持有界面对象，
 * 因此撤销栈可以独立存在。</p>
 */
public interface Command {

    /** 显示在历史面板中的名字。 */
    String name();

    /** 撤销：把状态恢复成执行之前。 */
    void undo(Document document);

    /** 重做：重新执行。 */
    void redo(Document document);

    /** 估算占用的字节数，用于控制内存。 */
    default long weight() {
        return 0;
    }
}
