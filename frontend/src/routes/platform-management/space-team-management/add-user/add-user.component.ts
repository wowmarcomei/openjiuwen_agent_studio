import {Component, Input, OnDestroy, OnInit, ViewEncapsulation,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MODULES} from '@shared/modules';
import {I18NEXT_NAMESPACE, I18NextEagerPipe} from 'angular-i18next';
import {I18nNamespace} from '@i18n';
import {SpaceTeamManagementService} from '@services/space-team-management.service';
import {CommonUtils} from '../../../../utils/common.util';
import {NzMessageService} from "ng-zorro-antd/message";
import {NzModalRef} from 'ng-zorro-antd/modal';
import {LocalAuthService} from '@services/local-auth.service';

interface SelectableWorkspaceUser {
  memberId: string;
  memberName: string;
  value: string;
  disabled: boolean;
}

interface WorkspaceMemberRecord {
  memberId: string;
  memberName?: string;
  role: string;
}

interface WorkspaceRoleRecord {
  roleId: string;
  roleNameCn: string;
  roleNameEn: string;
}

interface WorkspaceRoleOption {
  label: string;
  value: string;
  disabled: boolean;
}

@Component({
  selector: 'space-add-user',
  templateUrl: './add-user.component.html',
  styleUrls: ['./add-user.component.less'],
  encapsulation: ViewEncapsulation.None, // 要想设置的样式生效，此处必须配置成 ViewEncapsulation.None
  standalone: true,
  imports: [CommonModule, MODULES],
  providers: [
    {
      provide: I18NEXT_NAMESPACE,
      useValue: [I18nNamespace.PLATFORM_MANAGEMENT, I18nNamespace.COMMON],
    },
  ],
})
export class AddUserComponent implements OnInit, OnDestroy {
  @Input() space_id: '';

  btnLoading = false;

  search_value = '';
  checkedArray: SelectableWorkspaceUser[] = [];
  has_add_user_id: string[] = [];
  // 数据列表
  dataArray1: SelectableWorkspaceUser[] = [];
  originDataArray1: SelectableWorkspaceUser[] = [];
  integrationTabsOption: WorkspaceRoleOption[] = [];
  space_team_users: WorkspaceMemberRecord[] = [];
  roles_all: WorkspaceRoleRecord[] = [];
  checkAll = false;
  indeterminate = false;
  isLocalAuthentication = false;

  get canSubmit(): boolean {
    return this.checkedArray.length > 0 && this.checkedArray.every((item) => Boolean(item.value));
  }

  constructor(
    private i18n: I18NextEagerPipe,
    private spaceTeamManagementService: SpaceTeamManagementService,
    private message: NzMessageService,
    private modalRef: NzModalRef,
    private localAuthService: LocalAuthService,
  ) {}

 async ngOnInit() {
    this.isLocalAuthentication = Boolean(this.localAuthService.currentUser);
    await this.getSpaceMemberRoles();
    await this.get_space_members();
    await this.getAllUsersFn();
  }

  ngOnDestroy(): void {}

  close() {
    this.modalRef.destroy();
  }

  dismiss() {
    this.modalRef.destroy();
  }

  async addUserFn(): Promise<void> {
    const pendingUsers = this.checkedArray.filter(
      (item) => !this.has_add_user_id.includes(item.memberId),
    );
    if (pendingUsers.length === 0) {
      this.message.create('error', this.i18n.transform('no_person_selected'));
      return;
    }
    if (pendingUsers.some((item) => !item.value)) {
      this.message.create('error', this.i18n.transform('please_select_a_character'));
      return;
    }

    this.btnLoading = true;
    const memberSource = this.isLocalAuthentication ? 'INTERNAL' : 'IAM';
    const members = pendingUsers.map((item) => ({
      member_id: item.memberId,
      member_name: item.memberName,
      role: item.value,
      member_source: memberSource,
    }));
    const params = {
      members,
    };

    try {
      await this.spaceTeamManagementService.addSpaceMembers(params);
      this.message.create('success', this.i18n.transform('add_user_success'));
      this.close();
    } catch (error: any) {
      const errorMessage = error?.error?.error_msg_front
        || error?.error?.error_msg
        || error?.error?.message
        || this.i18n.transform('add_user_failed');
      this.message.create('error', errorMessage);
    } finally {
      this.btnLoading = false;
    }
  }

  async get_space_members() {
    const params = {
      page_num: 1,
      page_size: 9999,
    };
    await this.spaceTeamManagementService
      .getSpaceMembers(params)
      .then((res: any) => {
        this.space_team_users = res?.workspaceList || [];
      });
  }

  async getSpaceMemberRoles(): Promise<void> {
    const response: any = await this.spaceTeamManagementService.getSpaceMemberRoles();
    this.roles_all = response?.roleList || [];
    const lang = CommonUtils.getLanguage();
    this.integrationTabsOption = this.roles_all
      .filter((item) => item.roleId !== 'OWNER')
      .map((item) => ({
        label: lang === 'zh-cn' ? item.roleNameCn : CommonUtils.titleCase3(item.roleNameEn),
        value: item.roleId,
        disabled: false,
      }));
  }

  role_name(id: string): string {
    const role = this.roles_all.filter((item) => item.roleId === id);
    return role.length ? role[0].roleNameCn : '';
  }

  deleteUser(data: SelectableWorkspaceUser): void {
    this.checkedArray = this.checkedArray.filter(
      (item) => item.memberId !== data.memberId,
    );
    this.syncSelectAllState();
  }

  async getAllUsersFn() {
   await this.spaceTeamManagementService.getAllUsers().then((res: any) => {
      const list: SelectableWorkspaceUser[] = [];
      this.has_add_user_id = [];
      const defaultRole = this.integrationTabsOption.some((role) => role.value === 'DEVELOPER')
        ? 'DEVELOPER'
        : this.integrationTabsOption[0]?.value;
      res.workspaceList?.forEach((item) => {
        const existingMembers = this.space_team_users.filter(
          (arr) => item.memberId === arr.memberId,
        );
        if (existingMembers.length > 0) {
          const existingUser: SelectableWorkspaceUser = {
            memberId: item.memberId,
            memberName: item.memberName,
            value: existingMembers[0].role,
            disabled: true,
          };
          this.has_add_user_id.push(item.memberId);
          list.push(existingUser);
        } else {
          list.push({
            memberId: item.memberId,
            memberName: item.memberName,
            value: defaultRole,
            disabled: false,
          });
        }
      });
      this.dataArray1 = list;
      this.originDataArray1 = list;
      this.checkedArray = [];
      this.syncSelectAllState();
    });
  }

  isUserChecked(item: SelectableWorkspaceUser): boolean {
    return item.disabled || this.checkedArray.some((selected) => selected.memberId === item.memberId);
  }

  onUserChecked(item: SelectableWorkspaceUser, checked: boolean): void {
    if (item.disabled) {
      return;
    }

    const isSelected = this.checkedArray.some((selected) => selected.memberId === item.memberId);
    if (checked && !isSelected) {
      this.checkedArray = [...this.checkedArray, item];
    } else if (!checked && isSelected) {
      this.checkedArray = this.checkedArray.filter((selected) => selected.memberId !== item.memberId);
    }
    this.syncSelectAllState();
  }

  onSearch(value: string): void {
    const searchText = value?.trim().toLocaleLowerCase();
    if (!searchText) {
      this.dataArray1 = this.originDataArray1;
    } else {
      this.dataArray1 = this.originDataArray1.filter((item) =>
        item.memberName.toLocaleLowerCase().includes(searchText)
        || item.memberId.toLocaleLowerCase().includes(searchText),
      );
    }
    this.syncSelectAllState();
  }

  onNgcheckAll(checked: boolean): void {
    const visibleUserIds = new Set(
      this.dataArray1.filter((item) => !item.disabled).map((item) => item.memberId),
    );
    if (checked) {
      const selectedUsers = new Map(this.checkedArray.map((item) => [item.memberId, item]));
      this.dataArray1.filter((item) => !item.disabled).forEach((item) => {
        selectedUsers.set(item.memberId, item);
      });
      this.checkedArray = Array.from(selectedUsers.values());
    } else {
      this.checkedArray = this.checkedArray.filter((item) => !visibleUserIds.has(item.memberId));
    }
    this.syncSelectAllState();
  }

  trackByMemberId(_index: number, item: SelectableWorkspaceUser): string {
    return item.memberId;
  }

  private syncSelectAllState(): void {
    const visibleUsers = this.dataArray1.filter((item) => !item.disabled);
    const selectedCount = visibleUsers.filter((item) => this.isUserChecked(item)).length;
    this.checkAll = visibleUsers.length > 0 && selectedCount === visibleUsers.length;
    this.indeterminate = selectedCount > 0 && selectedCount < visibleUsers.length;
  }
}
