package com.kk.navigator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class SiteInfo {
    /** 未测速 */
    public static final long STATUS_UNTESTED = -1L;
    /** 超时 */
    public static final long STATUS_TIMEOUT = 88888L;
    /** 网络不可达 / 域名解析失败 */
    public static final long STATUS_UNREACHABLE = 99999L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private String content;
    private String url;
    private String title;
    private String description;

    private String checkinUrl;
    private String icon;
    private long status = STATUS_UNTESTED;
    /** 排序序号，越小越靠前；为空时按 id 排在最后 */
    private Integer sortOrder;
    /** 测速失败原因 */
    private String statusMsg;
    /** 最近一次测速时间 */
    private LocalDateTime checkedAt;

    /** 站点标签，按标签排序返回 */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "nav_site_tag",
            joinColumns = @JoinColumn(name = "site_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @OrderBy("sortOrder ASC, id ASC")
    private List<Tag> tags = new ArrayList<>();
}
