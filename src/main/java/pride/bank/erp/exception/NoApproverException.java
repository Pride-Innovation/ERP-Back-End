package pride.bank.erp.exception;

/**
 * A movement needs approval, but nobody could be resolved to give it.
 *
 * <p>Its own type rather than a plain {@link BadRequestException} so the client can recognise this
 * one case and offer to proceed anyway, without matching on the wording of a message. The handler
 * tags the response with {@code errorCode: NO_APPROVER}.
 *
 * <p>An empty chain nearly always means the initiator's reporting line is not configured — no branch
 * on their record, or nobody holding a ladder role above them in it — rather than that the movement
 * is genuinely unsupervisable.
 */
public class NoApproverException extends BadRequestException {
    public NoApproverException(String message) {
        super(message);
    }
}
