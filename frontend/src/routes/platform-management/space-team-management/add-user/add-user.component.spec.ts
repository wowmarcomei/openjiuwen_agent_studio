import { AddUserComponent } from './add-user.component';

describe('AddUserComponent', () => {
  let component: AddUserComponent;
  let spaceTeamManagementService: jasmine.SpyObj<any>;
  let message: jasmine.SpyObj<any>;
  let modalRef: jasmine.SpyObj<any>;

  const laomao = {
    memberId: 'laomao',
    memberName: '老猫',
    value: 'DEVELOPER',
    disabled: false,
  };

  beforeEach(() => {
    spaceTeamManagementService = jasmine.createSpyObj('SpaceTeamManagementService', [
      'addSpaceMembers',
      'getSpaceMemberRoles',
    ]);
    message = jasmine.createSpyObj('NzMessageService', ['create']);
    modalRef = jasmine.createSpyObj('NzModalRef', ['destroy']);
    component = new AddUserComponent(
      { transform: (key: string) => key } as any,
      spaceTeamManagementService,
      message,
      modalRef,
      { currentUser: { username: 'laomei' } } as any,
    );
    component.isLocalAuthentication = true;
    component.dataArray1 = [laomao];
    component.originDataArray1 = [laomao];
  });

  it('maps workspace roles to the label and value shape required by nz-select', async () => {
    spaceTeamManagementService.getSpaceMemberRoles.and.returnValue(Promise.resolve({
      roleList: [
        { roleId: 'OWNER', roleNameCn: '空间所有者', roleNameEn: 'OWNER' },
        { roleId: 'DEVELOPER', roleNameCn: '开发工程师', roleNameEn: 'DEVELOPER' },
      ],
    }));

    await component.getSpaceMemberRoles();

    expect(component.integrationTabsOption).toEqual([{
      label: '开发工程师',
      value: 'DEVELOPER',
      disabled: false,
    }]);
  });

  it('tracks an individually selected user by member id', () => {
    component.onUserChecked(laomao, true);

    expect(component.checkedArray.map((item) => item.memberId)).toEqual(['laomao']);
    expect(component.checkAll).toBeTrue();

    component.onUserChecked({ ...laomao }, false);

    expect(component.checkedArray).toEqual([]);
    expect(component.checkAll).toBeFalse();
  });

  it('searches by both display name and username', () => {
    const laomei = {
      memberId: 'laomei',
      memberName: '老梅',
      value: 'DEVELOPER',
      disabled: false,
    };
    component.dataArray1 = [laomao, laomei];
    component.originDataArray1 = [laomao, laomei];

    component.onSearch('LAOMAO');
    expect(component.dataArray1).toEqual([laomao]);

    component.onSearch('老梅');
    expect(component.dataArray1).toEqual([laomei]);
  });

  it('submits only newly selected users with the local member source', async () => {
    spaceTeamManagementService.addSpaceMembers.and.returnValue(Promise.resolve(1));
    component.checkedArray = [laomao];

    await component.addUserFn();

    expect(spaceTeamManagementService.addSpaceMembers).toHaveBeenCalledWith({
      members: [{
        member_id: 'laomao',
        member_name: '老猫',
        role: 'DEVELOPER',
        member_source: 'INTERNAL',
      }],
    });
    expect(modalRef.destroy).toHaveBeenCalled();
  });

  it('keeps the modal open and reports the error when adding fails', async () => {
    spaceTeamManagementService.addSpaceMembers.and.returnValue(
      Promise.reject({ error: { message: '添加失败' } }),
    );
    component.checkedArray = [laomao];

    await component.addUserFn();

    expect(message.create).toHaveBeenCalledWith('error', '添加失败');
    expect(modalRef.destroy).not.toHaveBeenCalled();
    expect(component.btnLoading).toBeFalse();
  });
});
