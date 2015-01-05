package model;

import java.io.Serializable;

/**
 * Please note that this class is not needed for Lab 1, but will later be
 * used in Lab 2. Hence, you do not have to implement it for the first
 * submission.
 */
public class ComputationRequestInfo implements Serializable, Comparable<ComputationRequestInfo>{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -2420582513780382631L;
	
	private String nodeName;
	private String operationRequest;
	private String operationResult;
	private String operationTimestamp;
	
	
	
	public ComputationRequestInfo(String nodeName, String operationRequest,
			String operationResult, String operationTimestamp) {
		super();
		this.nodeName = nodeName;
		this.operationRequest = operationRequest;
		this.operationResult = operationResult;
		this.operationTimestamp = operationTimestamp;
	}
	
	
	public String getNodeName() {
		return nodeName;
	}
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}
	public String getOperationRequest() {
		return operationRequest;
	}
	public void setOperationRequest(String operationRequest) {
		this.operationRequest = operationRequest;
	}
	public String getOperationResult() {
		return operationResult;
	}
	public void setOperationResult(String operationResult) {
		this.operationResult = operationResult;
	}
	public String getOperationTimestamp() {
		return operationTimestamp;
	}
	public void setOperationTimestamp(String operationTimestamp) {
		this.operationTimestamp = operationTimestamp;
	}


	@Override
	public String toString() {
		return operationTimestamp + " [" + nodeName + "]: "+operationRequest + " = "+operationResult;
	}


	@Override
	public int compareTo(ComputationRequestInfo arg0) {
		return getOperationTimestamp().compareTo(arg0.getOperationTimestamp());
	}
	
	
	
	
	
	
}
