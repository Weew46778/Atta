<?php

declare(strict_types=1);

namespace Atta;

use pocketmine\command\Command;
use pocketmine\command\CommandSender;
use pocketmine\entity\effect\EffectInstance;
use pocketmine\entity\effect\VanillaEffects;
use pocketmine\entity\Living;
use pocketmine\player\Player;
use pocketmine\plugin\PluginBase;
use pocketmine\Server;
use pocketmine\utils\TextFormat as TF;
use pocketmine\world\Explosion;

/**
 * پلاگین Atta — اجرای «کدهای کوتاه» اپراتوری روی سرور PocketMine-MP
 *
 * این پلاگین دقیقاً همان «مودِ دریافت‌کنندهٔ پالس» است:
 * اپ Atta با یک کلیک (RCON) فرمانی مثل «atta fly» می‌فرستد؛
 * این‌جا فرمان گرفته می‌شود و کدِ مخصوصش روی بازیکن/دنیا اجرا می‌شود.
 */
final class AttaPlugin extends PluginBase {

	private string $target = "";

	protected function onEnable(): void {
		$this->saveDefaultConfig();
		$this->target = (string) $this->getConfig()->get("target-player", "");
		$this->getLogger()->info(TF::GREEN . "پلاگین Atta فعال شد — فرمان: atta <code> [بازیکن]");
	}

	public function onCommand(CommandSender $sender, Command $command, string $label, array $args): bool {
		if (count($args) < 1) {
			$sender->sendMessage(TF::YELLOW . "استفاده: atta <کد> [بازیکن]  — مثال: atta fly، atta heal، atta boom Steve");
			$this->listCodes($sender);
			return true;
		}

		$code = strtolower(array_shift($args));
		$player = $this->resolvePlayer($sender, $args);
		if ($player === null) {
			$sender->sendMessage(TF::RED . "بازیکن پیدا نشد. (نام را دقیق بنویس یا در config.yml «target-player» را پر کن)");
			return true;
		}

		$this->runCode($player, $code, implode(" ", $args));
		return true;
	}

	/** پیدا کردن بازیکن: آرگومان نام → فرستنده اگر بازیکن است → تنظیمات */
	private function resolvePlayer(CommandSender $sender, array $args): ?Player {
		$name = $args[0] ?? ($sender instanceof Player ? $sender->getName() : $this->target);
		if ($name === "") return null;
		return $this->getServer()->getPlayerByPrefix($name);
	}

	private function msg(Player $p, string $t): void {
		try { $p->sendTip(TF::BOLD . TF::GREEN . "✓ " . $t); } catch (\Throwable $e) {}
	}

	/** توزیع کدها → اجرای کد مخصوص */
	private function runCode(Player $p, string $code, string $rest): void {
		switch ($code) {
			case "fly":
				$p->setAllowFlight(true);
				$p->setFlying(true);
				$this->msg($p, "پرواز روشن شد");
				break;
			case "unfly":
				$p->setFlying(false);
				$p->setAllowFlight(false);
				$this->msg($p, "پرواز خاموش شد");
				break;
			case "heal":
				$p->setHealth($p->getMaxHealth());
				$this->msg($p, "سلامتی کامل شد");
				break;
			case "feed":
				$p->getHungerManager()->setFood($p->getHungerManager()->getMaxFood());
				$p->getHungerManager()->setSaturation(20.0);
				$this->msg($p, "سیری کامل شد");
				break;
			case "clear":  // پاک‌سازی افکت‌ها
				$p->getEffects()->clear();
				$this->msg($p, "افکت‌ها پاک شد");
				break;
			case "god":
				$p->getEffects()->add(new EffectInstance(VanillaEffects::RESISTANCE(), 3600 * 20, 4, false));
				$p->getEffects()->add(new EffectInstance(VanillaEffects::REGENERATION(), 3600 * 20, 4, false));
				$this->msg($p, "حالت خدا روشن شد");
				break;
			case "speed":
				$lvl = max(0, min(10, ((int) $rest) - 1));
				$p->getEffects()->add(new EffectInstance(VanillaEffects::SPEED(), 3600 * 20, $lvl, false));
				$this->msg($p, "سرعت تنظیم شد");
				break;
			case "jump":
				$lvl = max(0, min(10, ((int) $rest) - 1));
				$p->getEffects()->add(new EffectInstance(VanillaEffects::JUMP_BOOST(), 3600 * 20, $lvl, false));
				$this->msg($p, "پرش تنظیم شد");
				break;
			case "night":
				$p->getWorld()->setTime(18000);
				$this->msg($p, "شب شد");
				break;
			case "day":
				$p->getWorld()->setTime(6000);
				$this->msg($p, "روز شد");
				break;
			case "sun":
				$p->getWorld()->setRain(false);
				$p->getWorld()->setThunder(false);
				$this->msg($p, "هوا صاف شد");
				break;
			case "rain":
				$p->getWorld()->setRain(true);
				$p->getWorld()->setThunder(false);
				$this->msg($p, "باران شروع شد");
				break;
			case "storm":
				$p->getWorld()->setRain(true);
				$p->getWorld()->setThunder(true);
				$this->msg($p, "طوفان شروع شد");
				break;
			case "boom":
				$expl = new Explosion($p->getPosition(), 4.0, $p);
				$expl->explodeA();
				$this->msg($p, "انفجار!");
				break;
			case "top":
				$w = $p->getWorld();
				$x = (int) floor($p->getPosition()->x);
				$z = (int) floor($p->getPosition()->z);
				$y = $w->getHighestBlockAt($x, $z);
				$p->teleport(new \pocketmine\world\Position($x + 0.5, $y + 1.0, $z + 0.5, $w));
				$this->msg($p, "انتقال به بلندترین نقطه");
				break;
			case "spawn":
				$p->teleport($p->getWorld()->getSpawnLocation());
				$this->msg($p, "انتقال به نقطهٔ تولد");
				break;
			case "giant":
				$p->setScale(2.0);
				$this->msg($p, "غول شدی!");
				break;
			case "mini":
				$p->setScale(0.5);
				$this->msg($p, "کوچک شدی!");
				break;
			case "xp":
				$n = max(1, min(99999, (int) $rest === 0 ? 100 : (int) $rest));
				$p->getXpManager()->addXp($n);
				$this->msg($p, "تجربه +" . $n);
				break;
			case "inv":
				$p->getInventory()->clearAll();
				$this->msg($p, "کیف پاک شد");
				break;
			case "give":
				$this->giveCode($p, $rest);
				break;
			default:
				$p->sendMessage(TF::RED . "کد ناشناخته: " . $code);
				$this->listCodes($p);
		}
	}

	private function giveCode(Player $p, string $rest): void {
		$parts = preg_split('/\s+/', trim($rest));
		$item = $parts[0] ?? "";
		$count = max(1, (int) ($parts[1] ?? 1));
		if ($item === "") {
			$p->sendMessage(TF::YELLOW . "استفاده: atta give <آیتم> [تعداد]");
			return;
		}
		// تبدیل نام به آیتم — پشتیبانی از هر دو API قدیمی/جدید PMMP
		try {
			$factory = \pocketmine\item\ItemFactory::getInstance();
			$itemObj = $factory->getByName($item);
			if ($itemObj === null || $itemObj->isNull()) {
				$itemObj = $factory->get("minecraft:" . $item);
			}
		} catch (\Throwable $e) {
			try {
				$factory = \pocketmine\item\ItemFactory::getInstance();
				$itemObj = $factory->get("minecraft:" . $item);
			} catch (\Throwable $e2) {
				$p->sendMessage(TF::RED . "آیتم پیدا نشد. (مثل: diamond, netherite_sword, golden_apple)");
				return;
			}
		}
		if ($itemObj->isNull()) {
			$p->sendMessage(TF::RED . "آیتم پیدا نشد. (مثل: diamond, netherite_sword, golden_apple)");
			return;
		}
		$itemObj->setCount($count);
		$p->getInventory()->addItem($itemObj);
		$this->msg($p, "آیتم داده شد: " . $item . " ×" . $count);
	}

	private function listCodes(CommandSender $s): void {
		$codes = [
			"fly / unfly", "heal", "feed", "clear", "god", "speed <1-10>", "jump <1-10>",
			"night / day", "sun / rain / storm", "boom", "top", "spawn", "giant / mini",
			"xp <تعداد>", "inv", "give <آیتم> [تعداد]",
		];
		$s->sendMessage(TF::GRAY . "کدها: " . TF::WHITE . implode(TF::GRAY . " | " . TF::WHITE, $codes));
	}
}
