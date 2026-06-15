package com.example.demo.application.domain.role.aggregate.vo;


/**
 * <h2>[領域層 - 值物件] 權限點值物件 (Permission Value Object)</h2> *
 * <p>
 * 完美遵循 DDD 值物件鐵律： 1. <b>完全不可變性 (Immutable)</b>：無標識（Identity），完全由屬性定義等價性。<br>
 * 2. <b>子系統聯防行為</b>：核心方法 {@link #isSamePermission} 點破了業務不變性——只要兩個權限點的
 * {@code systemCode} 與 {@code permissionCode} 相同，
 * 資料庫便將其視為「同一個權限點」，自動忽略名稱改動，完美配合子系統自動化冪等上報。
 * </p>
 */
public record Permission(String systemCode, String permissionCode, String permissionName) {

	/**
	 * 緊湊建構式 (Compact Constructor) 進行強固的入參防禦 (Guard Clauses)
	 */
	public Permission{if(systemCode==null||systemCode.isBlank()){throw new IllegalArgumentException("System code cannot be empty");}if(permissionCode==null||permissionCode.isBlank()){throw new IllegalArgumentException("Permission code cannot be empty");}if(permissionName==null||permissionName.isBlank()){throw new IllegalArgumentException("Permission name cannot be empty");}}

	/**
	 * <b>【值物件等價判定】判斷是否為同一個核心權限點</b>
	 * <p>
	 * 核心邏輯：只要系統別（systemCode）與權限代碼（permissionCode）相同，即視為相同，忽略名稱描述的差異。
	 * 這在外部系統「權限重整上報」時，能發揮自動更名的效果。
	 * </p>
	 */
	public boolean isSamePermission(String systemCode, String permissionCode) {
		return this.systemCode.equals(systemCode) && this.permissionCode.equals(permissionCode);
	}
}