package kz.clientflow.workspace;

/**
 * Roles inside a workspace and what each role may do.
 *
 * | Permission                         | OWNER | MANAGER | EMPLOYEE        |
 * |------------------------------------|-------|---------|-----------------|
 * | Manage members, rename workspace   |  yes  |   no    |   no            |
 * | Create / edit / archive clients    |  yes  |   yes   |   no (read only)|
 * | Create / edit / archive orders     |  yes  |   yes   |   no            |
 * | See orders                         |  all  |   all   |   assigned only |
 * | Change order status                |  any  |   any   |   assigned, forward only |
 * | Record and void payments, reports  |  yes  |   yes   |   no            |
 */
public enum WorkspaceRole {
    OWNER, MANAGER, EMPLOYEE;

    public boolean canManageMembers() {
        return this == OWNER;
    }

    /** Clients, orders, payments and reports. */
    public boolean canManageRecords() {
        return this == OWNER || this == MANAGER;
    }

    public boolean seesAllOrders() {
        return this == OWNER || this == MANAGER;
    }
}
