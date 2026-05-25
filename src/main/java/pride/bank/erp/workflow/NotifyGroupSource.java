package pride.bank.erp.workflow;

/**
 * How a notification target (CC for a pending step, or a completion notification)
 * is resolved at runtime.
 *
 * <ul>
 *   <li>{@link #STATIC} — use the literal {@code notifyGroupEmail} string saved on the step.
 *       Preserves the legacy behaviour and is the default for back-filled rows.</li>
 *   <li>{@link #REQUESTER_MANAGERS_GROUP} — managers distribution list of the requester's
 *       duty station ({@code department.managersGroupEmail} at HO, else
 *       {@code branch.managersGroupEmail}). One workflow then covers every department / branch.</li>
 *   <li>{@link #REQUESTER} — the user who raised the request.</li>
 *   <li>{@link #REQUESTER_DIRECT_SUPERVISOR} — the requester's direct supervisor in the same
 *       duty station (resolved via {@code title.reportsTo}).</li>
 *   <li>{@link #ADMIN} — the application admin (config {@code application.admin.email}).</li>
 *   <li>{@link #NONE} — no notification.</li>
 * </ul>
 *
 * Used by two fields on {@code ApprovalStep}:
 * {@code notifyGroupSource} (CC sent when the step becomes pending) and
 * {@code notifyOnCompletion} (set of targets emailed when the step is completed).
 */
public enum NotifyGroupSource {
    STATIC,
    REQUESTER_MANAGERS_GROUP,
    REQUESTER,
    REQUESTER_DIRECT_SUPERVISOR,
    ADMIN,
    NONE
}
