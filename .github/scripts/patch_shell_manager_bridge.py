from pathlib import Path

path = Path('app/src/main/java/com/gree1d/reappzuku/core/ShellManager.java')
text = path.read_text()

field_anchor = '    private final ExecutorService executor;\n'
if field_anchor not in text:
    raise SystemExit('executor field anchor not found')
text = text.replace(
    field_anchor,
    field_anchor + '    private final ShizukuBridge shizuku;\n',
    1)

old_ctor = '''    public ShellManager(Context context, Handler handler, ExecutorService executor) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.executor = executor;

        // Keep an application-lifetime listener. Activity listeners can be removed
        // while the system permission dialog is in the foreground, so binding the
        // UserService must not depend on an Activity still being started.
        Shizuku.addRequestPermissionResultListener(internalShizukuPermissionListener);
        Shizuku.addBinderReceivedListenerSticky(internalBinderReceivedListener);
        Shizuku.addBinderDeadListener(internalBinderDeadListener);
    }
'''
new_ctor = '''    public ShellManager(Context context, Handler handler, ExecutorService executor) {
        this(context, handler, executor, new RealShizukuBridge());
    }

    ShellManager(Context context, Handler handler, ExecutorService executor, ShizukuBridge shizuku) {
        if (shizuku == null) throw new IllegalArgumentException("shizuku == null");
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.executor = executor;
        this.shizuku = shizuku;

        // Keep an application-lifetime listener. Activity listeners can be removed
        // while the system permission dialog is in the foreground, so binding the
        // UserService must not depend on an Activity still being started.
        shizuku.addRequestPermissionResultListener(internalShizukuPermissionListener);
        shizuku.addBinderReceivedListenerSticky(internalBinderReceivedListener);
        shizuku.addBinderDeadListener(internalBinderDeadListener);
    }
'''
if old_ctor not in text:
    raise SystemExit('constructor anchor not found')
text = text.replace(old_ctor, new_ctor, 1)

for old, new in [
    ('Shizuku.pingBinder()', 'shizuku.pingBinder()'),
    ('Shizuku.checkSelfPermission()', 'shizuku.checkSelfPermission()'),
    ('Shizuku.bindUserService(buildUserServiceArgs(), userServiceConnection)',
     'shizuku.bindUserService(buildUserServiceArgs(), userServiceConnection)'),
    ('Shizuku.unbindUserService(buildUserServiceArgs(), userServiceConnection, true)',
     'shizuku.unbindUserService(buildUserServiceArgs(), userServiceConnection, true)'),
    ('Shizuku.addRequestPermissionResultListener(listener)',
     'shizuku.addRequestPermissionResultListener(listener)'),
    ('Shizuku.removeRequestPermissionResultListener(listener)',
     'shizuku.removeRequestPermissionResultListener(listener)'),
    ('Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)',
     'shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)'),
    ('Shizuku.addBinderDeadListener(shizukuBinderDeadListener)',
     'shizuku.addBinderDeadListener(shizukuBinderDeadListener)'),
    ('Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)',
     'shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)'),
    ('Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)',
     'shizuku.removeBinderDeadListener(shizukuBinderDeadListener)'),
    ('Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)',
     'shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)'),
]:
    if old not in text:
        raise SystemExit(f'expected Shizuku call not found: {old}')
    text = text.replace(old, new)

for forbidden in [
    'Shizuku.pingBinder()',
    'Shizuku.checkSelfPermission()',
    'Shizuku.bindUserService(',
    'Shizuku.unbindUserService(',
    'Shizuku.addRequestPermissionResultListener(',
    'Shizuku.removeRequestPermissionResultListener(',
    'Shizuku.addBinderReceivedListenerSticky(',
    'Shizuku.removeBinderReceivedListener(',
    'Shizuku.addBinderDeadListener(',
    'Shizuku.removeBinderDeadListener(',
    'Shizuku.requestPermission(',
]:
    if forbidden in text:
        raise SystemExit(f'unmigrated static Shizuku operation remains: {forbidden}')

path.write_text(text)
